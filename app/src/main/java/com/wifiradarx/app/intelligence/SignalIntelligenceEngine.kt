package com.wifiradarx.app.intelligence

import kotlin.math.*

/**
 * Central intelligence orchestrator.
 * Wraps IDW, PathLoss, DeadZone, ChannelAnalyzer, RogueAp, EMFFingerprinter, Temporal.
 */
class SignalIntelligenceEngine {

    val idwInterpolator = IdwInterpolator(power = 2.0)
    val pathLossModel = PathLossModel(PathLossModel.Preset.OFFICE)
    val deadZoneDetector = DeadZoneDetector()
    val rogueApDetector = RogueApDetector()
    val emfFingerprinter = EMFFingerprinter()
    val temporalProfiler = TemporalProfiler()
    val interferenceTriangulator = InterferenceTriangulator()
    val saOptimizer = SimulatedAnnealingOptimizer()

    data class ScanSnapshot(
        val bssid: String,
        val ssid: String,
        val rssi: Int,
        val frequency: Int,
        val channel: Int,
        val capabilities: String,
        val vendorOui: String,
        val posX: Float,
        val posY: Float,
        val posZ: Float,
        val timestampMicros: Long
    )

    data class InsightReport(
        val deadZones: List<DeadZoneDetector.DeadZone>,
        val apRecommendations: List<DeadZoneDetector.ApRecommendation>,
        val channelStats: Map<Int, ChannelAnalyzer.ChannelStats>,
        val bestChannels24: List<ChannelAnalyzer.ChannelRecommendation>,
        val bestChannels5: List<ChannelAnalyzer.ChannelRecommendation>,
        val rogueAlerts: List<RogueApDetector.ThreatAssessment>,
        val anomalies: List<TemporalProfiler.AnomalyEvent>,
        val zScores: Map<String, Float>,
        val directionMap: Map<String, Float>, // bssid -> bearing degrees
        val trend: Map<String, Float>          // bssid -> trend dbm/scan
    )

    private val rssiHistory = mutableMapOf<String, MutableList<Float>>() // bssid -> history
    private val HISTORY_SIZE = 20

    fun processScan(snapshots: List<ScanSnapshot>, hourOfWeek: Int): InsightReport {
        // Feed IDW
        snapshots.forEach { s ->
            idwInterpolator.addSample(s.posX, s.posZ, s.rssi.toFloat(), s.posY)
        }

        // Feed temporal
        val anomalies = mutableListOf<TemporalProfiler.AnomalyEvent>()
        snapshots.forEach { s ->
            temporalProfiler.update(s.bssid, hourOfWeek, s.rssi.toFloat())
            temporalProfiler.checkAnomaly(s.bssid, hourOfWeek, s.rssi.toFloat())?.let {
                anomalies.add(it)
            }
            // feed EMF
            if (s.timestampMicros > 0L) emfFingerprinter.addObservation(s.bssid, s.timestampMicros)
        }

        // Z-score anomaly detection per snapshot
        val zScores = mutableMapOf<String, Float>()
        val rssiValues = snapshots.map { it.rssi.toFloat() }
        val globalMean = rssiValues.average().toFloat()
        val globalStd = rssiValues.map { (it - globalMean).pow(2) }.average().toFloat().let { sqrt(it) }
        snapshots.forEach { s ->
            zScores[s.bssid] = if (globalStd < 0.1f) 0f else (s.rssi - globalMean) / globalStd
        }

        // Trend per BSSID
        val trends = mutableMapOf<String, Float>()
        snapshots.forEach { s ->
            val hist = rssiHistory.getOrPut(s.bssid) { mutableListOf() }
            hist.add(s.rssi.toFloat())
            if (hist.size > HISTORY_SIZE) hist.removeAt(0)
            if (hist.size >= 3) {
                trends[s.bssid] = linearTrend(hist)
            }
        }

        // Direction map (8-direction bearing to strongest signal per SSID group)
        val dirMap = mutableMapOf<String, Float>()
        val strongest = snapshots.groupBy { it.ssid }.mapValues { (_, v) -> v.maxByOrNull { it.rssi } }
        strongest.values.filterNotNull().forEach { s ->
            val bearing = atan2(s.posX.toDouble(), s.posZ.toDouble()).toFloat()
            dirMap[s.bssid] = Math.toDegrees(bearing.toDouble()).toFloat()
        }

        // Channel analysis
        val netPairs = snapshots.map { it.frequency to it.rssi }
        val chStats = ChannelAnalyzer.analyzeChannels(netPairs)
        val best24 = ChannelAnalyzer.getBestChannels(chStats, ChannelAnalyzer.Band.GHZ_2_4)
        val best5 = ChannelAnalyzer.getBestChannels(chStats, ChannelAnalyzer.Band.GHZ_5)

        // Rogue AP detection
        val rogueAlerts = snapshots.map { s ->
            rogueApDetector.assess(s.bssid, s.ssid, s.channel, s.capabilities, s.vendorOui, s.rssi)
        }.filter { it.isAlert }

        // Dead zones from IDW grid
        val bounds = idwInterpolator.getBounds()
        val deadZones = if (bounds != null && idwInterpolator.getSampleCount() >= 4) {
            val grid = idwInterpolator.buildGrid(bounds[0], bounds[1], bounds[2], bounds[3], 24)
            deadZoneDetector.findDeadZones(grid, bounds[0], bounds[1], bounds[2], bounds[3])
        } else emptyList()
        val apRecs = deadZoneDetector.recommendApPlacements(deadZones)

        return InsightReport(
            deadZones, apRecs, chStats, best24, best5,
            rogueAlerts, anomalies, zScores, dirMap, trends
        )
    }

    private fun linearTrend(values: List<Float>): Float {
        val n = values.size
        val xs = (0 until n).map { it.toFloat() }
        val ys = values
        val sumX = xs.sum(); val sumY = ys.sum()
        val sumXY = xs.zip(ys).sumOf { (x, y) -> (x * y).toDouble() }.toFloat()
        val sumX2 = xs.sumOf { (it * it).toDouble() }.toFloat()
        val denom = n * sumX2 - sumX * sumX
        return if (abs(denom) < 1e-6f) 0f else (n * sumXY - sumX * sumY) / denom
    }

    private fun Float.pow(n: Int): Float { var r = 1f; repeat(n) { r *= this }; return r }

    fun clearHistory() {
        rssiHistory.clear()
        idwInterpolator.clearSamples()
    }
}
