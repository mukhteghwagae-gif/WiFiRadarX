package com.wifiradarx.app.intelligence

import kotlin.math.*
import kotlin.random.Random

// ─── Simulated Annealing Channel Optimizer ───────────────────────────────────

/**
 * Multi-AP channel assignment via simulated annealing.
 * Minimizes weighted inter-AP interference energy.
 */
class SimulatedAnnealingOptimizer {

    data class ApNode(
        val id: String,
        val x: Float,
        val y: Float,
        val band: ChannelAnalyzer.Band,
        var assignedChannel: Int = 0
    )

    private val channels24 = listOf(1, 6, 11)
    private val channels5 = listOf(36, 40, 44, 48, 149, 153, 157, 161, 165)
    private val channels6 = listOf(1, 5, 9, 13, 17, 21, 25, 29, 33, 37)

    fun optimize(
        aps: List<ApNode>,
        tInit: Double = 1000.0,
        coolingRate: Double = 0.995,
        maxIterations: Int = 5000
    ): List<ApNode> {
        if (aps.isEmpty()) return aps
        val state = aps.map { it.copy() }.toMutableList()

        // Initial random assignment
        state.forEach { ap -> ap.assignedChannel = randomChannel(ap.band) }

        var T = tInit
        var energy = computeEnergy(state)
        val best = state.map { it.copy() }.toMutableList()
        var bestEnergy = energy

        repeat(maxIterations) {
            if (T < 0.1) return@repeat

            // Perturb: change one random AP's channel
            val idx = Random.nextInt(state.size)
            val ap = state[idx]
            val oldCh = ap.assignedChannel
            ap.assignedChannel = randomChannel(ap.band)

            val newEnergy = computeEnergy(state)
            val delta = newEnergy - energy
            val accept = delta < 0 || Random.nextDouble() < exp(-delta / T)

            if (accept) {
                energy = newEnergy
                if (energy < bestEnergy) {
                    bestEnergy = energy
                    state.forEachIndexed { i, a -> best[i] = a.copy() }
                }
            } else {
                ap.assignedChannel = oldCh // revert
            }
            T *= coolingRate
        }
        return best
    }

    private fun computeEnergy(aps: List<ApNode>): Double {
        var energy = 0.0
        for (i in aps.indices) {
            for (j in i + 1 until aps.size) {
                val a = aps[i]; val b = aps[j]
                if (a.band != b.band) continue
                val dist = sqrt(((a.x - b.x).pow(2) + (a.y - b.y).pow(2)).toDouble())
                val distFactor = if (dist < 0.1) 100.0 else 1.0 / dist
                val channelOverlap = channelOverlap(a.band, a.assignedChannel, b.assignedChannel)
                energy += distFactor * channelOverlap
            }
        }
        return energy
    }

    private fun channelOverlap(band: ChannelAnalyzer.Band, ch1: Int, ch2: Int): Double {
        if (ch1 == ch2) return 10.0
        return when (band) {
            ChannelAnalyzer.Band.GHZ_2_4 -> maxOf(0.0, 5.0 - abs(ch1 - ch2).toDouble())
            else -> if (abs(ch1 - ch2) < 4) 3.0 else 0.0
        }
    }

    private fun randomChannel(band: ChannelAnalyzer.Band): Int {
        val list = when (band) {
            ChannelAnalyzer.Band.GHZ_2_4 -> channels24
            ChannelAnalyzer.Band.GHZ_5 -> channels5
            ChannelAnalyzer.Band.GHZ_6 -> channels6
        }
        return list[Random.nextInt(list.size)]
    }

    private fun Float.pow(n: Int): Float { var r = 1f; repeat(n) { r *= this }; return r }
}

// ─── Interference Triangulator ───────────────────────────────────────────────

/**
 * Triangulates interference source from (x, z, score) tuples via gradient descent.
 */
class InterferenceTriangulator {

    enum class InterferenceType(val displayName: String) {
        MICROWAVE("Microwave Oven"),
        BABY_MONITOR("Baby Monitor"),
        BLUETOOTH("Bluetooth Device"),
        RADAR("Radar/DECT Phone"),
        UNKNOWN("Unknown Source")
    }

    data class Measurement(val x: Float, val z: Float, val score: Float)
    data class Source(val x: Float, val z: Float, val type: InterferenceType, val confidence: Float)

    private val measurements = mutableListOf<Measurement>()

    fun addMeasurement(x: Float, z: Float, interferenceScore: Float) {
        measurements.add(Measurement(x, z, interferenceScore))
    }

    fun clearMeasurements() = measurements.clear()
    fun getMeasurementCount() = measurements.size

    /**
     * Gradient descent to find source (x, z) minimizing sum of |predicted - measured|^2.
     * Model: score ∝ 1 / dist^2 — so source is at highest score concentration.
     */
    fun triangulate(): Source? {
        if (measurements.size < 3) return null

        // Initial estimate: weighted centroid
        val totalScore = measurements.sumOf { it.score.toDouble() }.toFloat()
        if (totalScore < 1e-6f) return null
        var srcX = measurements.sumOf { (it.x * it.score).toDouble() }.toFloat() / totalScore
        var srcZ = measurements.sumOf { (it.z * it.score).toDouble() }.toFloat() / totalScore

        // Gradient descent
        var lr = 0.1f
        repeat(500) {
            var gradX = 0.0; var gradZ = 0.0
            for (m in measurements) {
                val dx = (srcX - m.x).toDouble()
                val dz = (srcZ - m.z).toDouble()
                val dist2 = dx * dx + dz * dz + 1e-4
                val predicted = 100.0 / dist2
                val err = predicted - m.score.toDouble()
                gradX += err * (-2.0 * dx / (dist2 * dist2))
                gradZ += err * (-2.0 * dz / (dist2 * dist2))
            }
            srcX -= (lr * gradX).toFloat()
            srcZ -= (lr * gradZ).toFloat()
            if (it % 100 == 99) lr *= 0.5f
        }

        val confidence = computeConfidence(srcX, srcZ)
        return Source(srcX, srcZ, classifyType(), confidence)
    }

    private fun computeConfidence(srcX: Float, srcZ: Float): Float {
        var err = 0.0
        for (m in measurements) {
            val dx = (srcX - m.x).toDouble()
            val dz = (srcZ - m.z).toDouble()
            val dist2 = dx * dx + dz * dz + 1e-4
            val predicted = 100.0 / dist2
            err += (predicted - m.score).pow(2.0)
        }
        val rmse = sqrt(err / measurements.size)
        return (1f - (rmse / 100.0).toFloat()).coerceIn(0f, 1f)
    }

    // Classify by score variance pattern (simplified)
    private fun classifyType(): InterferenceType {
        if (measurements.isEmpty()) return InterferenceType.UNKNOWN
        val scores = measurements.map { it.score }
        val mean = scores.average().toFloat()
        val variance = scores.map { (it - mean).pow(2) }.average().toFloat()
        return when {
            mean > 70 && variance < 100 -> InterferenceType.MICROWAVE
            mean in 40f..70f && variance < 200 -> InterferenceType.BABY_MONITOR
            variance > 500 -> InterferenceType.BLUETOOTH
            mean < 40f -> InterferenceType.RADAR
            else -> InterferenceType.UNKNOWN
        }
    }

    private fun Float.pow(n: Int): Float { var r = 1f; repeat(n) { r *= this }; return r }
    private fun Double.pow(n: Double): Double = Math.pow(this, n)
}

// ─── Wall Material Inferencer ─────────────────────────────────────────────────

/**
 * Infers wall material from dB attenuation per wall.
 */
object WallMaterialInferencer {

    enum class Material(val displayName: String, val colorHex: Int, val minDb: Float, val maxDb: Float) {
        GLASS("Glass", 0x8000D4FF.toInt(), 2f, 5f),
        DRYWALL("Drywall", 0x80AAFFAA.toInt(), 3f, 7f),
        BRICK("Brick", 0x80FF8844.toInt(), 6f, 14f),
        CONCRETE("Concrete", 0x80888888.toInt(), 10f, 20f),
        METAL("Metal/Foil", 0x80FF4444.toInt(), 20f, 40f),
        UNKNOWN("Unknown", 0x80FFFFFF.toInt(), 0f, 999f)
    }

    /** Infer material from dB loss per detected plane. */
    fun infer(attenuationDb: Float): Material {
        return Material.values().firstOrNull {
            it != Material.UNKNOWN && attenuationDb in it.minDb..it.maxDb
        } ?: Material.UNKNOWN
    }

    /**
     * Compare RSSI on both sides of a plane.
     * @param rssiNear RSSI measured before the plane
     * @param rssiFar RSSI measured after the plane
     */
    fun inferFromRssi(rssiNear: Int, rssiFar: Int): Material {
        val attenuation = (rssiNear - rssiFar).toFloat().coerceAtLeast(0f)
        return infer(attenuation)
    }
}

// ─── Temporal Profiler ────────────────────────────────────────────────────────

/**
 * Learns hourly/daily RSSI baselines via exponential moving average.
 * Fires anomaly events when current value deviates significantly.
 */
class TemporalProfiler(private val alpha: Float = 0.15f) {

    data class Baseline(
        val hourOfWeek: Int,
        var emaRssi: Float,
        var emaVariance: Float,
        var sampleCount: Int = 0
    )

    data class AnomalyEvent(
        val bssid: String,
        val hourOfWeek: Int,
        val currentRssi: Float,
        val expectedRssi: Float,
        val zScore: Float,
        val message: String
    )

    // bssid -> hourOfWeek -> Baseline
    private val baselines = mutableMapOf<String, MutableMap<Int, Baseline>>()

    fun update(bssid: String, hourOfWeek: Int, rssi: Float) {
        val apMap = baselines.getOrPut(bssid) { mutableMapOf() }
        val existing = apMap[hourOfWeek]
        if (existing == null) {
            apMap[hourOfWeek] = Baseline(hourOfWeek, rssi, 0f, 1)
        } else {
            val newEma = alpha * rssi + (1 - alpha) * existing.emaRssi
            val diff = rssi - existing.emaRssi
            val newVar = alpha * diff * diff + (1 - alpha) * existing.emaVariance
            existing.emaRssi = newEma
            existing.emaVariance = newVar
            existing.sampleCount++
        }
    }

    fun checkAnomaly(bssid: String, hourOfWeek: Int, rssi: Float): AnomalyEvent? {
        val baseline = baselines[bssid]?.get(hourOfWeek) ?: return null
        if (baseline.sampleCount < 5) return null
        val std = sqrt(baseline.emaVariance)
        if (std < 0.1f) return null
        val z = (rssi - baseline.emaRssi) / std
        return if (abs(z) > 2.5f) {
            AnomalyEvent(
                bssid, hourOfWeek, rssi, baseline.emaRssi, z,
                if (z > 0) "Signal stronger than usual (${z.format(1)}σ)"
                else "Signal weaker than usual (${(-z).format(1)}σ)"
            )
        } else null
    }

    fun getBaseline(bssid: String, hourOfWeek: Int): Baseline? =
        baselines[bssid]?.get(hourOfWeek)

    fun clearBaselines() = baselines.clear()

    private fun Float.format(decimals: Int) = "%.${decimals}f".format(this)
}
