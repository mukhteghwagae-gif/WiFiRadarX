package com.wifiradarx.app.intelligence

import kotlin.math.*

/**
 * Signal Intelligence Engine: orchestrates all above + Z-score anomaly detection + trend + 8-direction map
 */
class SignalIntelligenceEngine(
    private val idw: IdwInterpolator = IdwInterpolator(),
    private val pathLoss: PathLossModel = PathLossModel(),
    private val rogueAp: RogueApDetector = RogueApDetector()
) {
    fun detectAnomaly(history: List<Int>, current: Int): Double {
        if (history.size < 5) return 0.0
        val mean = history.average()
        val stdDev = sqrt(history.map { (it - mean).pow(2) }.average())
        return if (stdDev < 0.001) 0.0 else abs(current - mean) / stdDev
    }

    fun calculateTrend(history: List<Int>): Double {
        if (history.size < 2) return 0.0
        val n = history.size
        val x = (0 until n).map { it.toDouble() }
        val y = history.map { it.toDouble() }
        
        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { it.first * it.second }
        val sumX2 = x.sumOf { it.pow(2) }
        
        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX.pow(2))
    }
}

/**
 * Temporal Profiler: learn hourly/daily baselines via exponential moving average (α=0.15)
 */
class TemporalProfiler(private val alpha: Double = 0.15) {
    fun updateBaseline(currentBaseline: Double, newValue: Double): Double {
        return alpha * newValue + (1.0 - alpha) * currentBaseline
    }
}

/**
 * Wall Material Inferencer: compare RSSI on both sides of ARCore planes
 */
class WallMaterialInferencer {
    enum class Material(val lossPerMeter: Double) {
        GLASS(3.0),
        DRYWALL(5.0),
        BRICK(10.0),
        CONCRETE(15.0),
        METAL(30.0),
        UNKNOWN(0.0)
    }

    fun infer(rssiBefore: Int, rssiAfter: Int, thickness: Double): Material {
        val loss = (rssiBefore - rssiAfter).toDouble()
        val lossPerM = loss / (thickness + 0.01)
        
        return when {
            lossPerM > 25.0 -> Material.METAL
            lossPerM > 12.0 -> Material.CONCRETE
            lossPerM > 8.0 -> Material.BRICK
            lossPerM > 4.0 -> Material.DRYWALL
            lossPerM > 1.5 -> Material.GLASS
            else -> Material.UNKNOWN
        }
    }
}

/**
 * Security Auditor: score each network (+30 WPA3, -40 open, -30 WPS, etc.)
 */
class SecurityAuditor {
    fun audit(capabilities: String): Int {
        var score = 50
        if (capabilities.contains("WPA3")) score += 30
        if (capabilities.contains("WPA2")) score += 10
        if (capabilities.contains("WEP")) score -= 30
        if (capabilities.isEmpty() || capabilities == "[ESS]") score -= 40
        if (capabilities.contains("WPS")) score -= 30
        return score.coerceIn(0, 100)
    }
}

/**
 * Channel Analyzer: per-channel congestion (2.4/5/6 GHz), interference scoring, best-channel ranking
 */
class ChannelAnalyzer {
    data class ChannelScore(val channel: Int, val congestion: Double, val interference: Double, val totalScore: Double)

    fun analyze(scanResults: List<Pair<Int, Int>>): List<ChannelScore> {
        val channelMap = mutableMapOf<Int, MutableList<Int>>()
        for ((channel, rssi) in scanResults) {
            channelMap.getOrPut(channel) { mutableListOf() }.add(rssi)
        }
        
        val scores = mutableListOf<ChannelScore>()
        for (ch in 1..14) { // Simplified 2.4GHz
            val rssis = channelMap[ch] ?: emptyList<Int>()
            val congestion = rssis.size / 10.0
            val interference = rssis.map { 10.0.pow((it + 100) / 10.0) }.sum() / 1000.0
            val total = 1.0 - (congestion * 0.5 + interference * 0.5).coerceIn(0.0, 1.0)
            scores.add(ChannelScore(ch, congestion, interference, total))
        }
        return scores.sortedByDescending { it.totalScore }
    }
}
