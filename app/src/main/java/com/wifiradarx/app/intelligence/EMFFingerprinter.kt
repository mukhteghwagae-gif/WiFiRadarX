package com.wifiradarx.app.intelligence

import kotlin.math.*

/**
 * Builds EMF fingerprint from ScanResult timestamp inter-arrival times.
 * Extracts 12-dimensional feature vector: mean, variance, skewness, kurtosis,
 * clock drift, beacon interval, probe burst count, and 5 spectral features.
 */
class EMFFingerprinter {

    data class Fingerprint(
        val bssid: String,
        val featureVector: FloatArray, // 12-dim
        val beaconIntervalMs: Float,
        val clockDriftPpm: Float,
        val macRandomized: Boolean
    ) {
        override fun equals(other: Any?) = other is Fingerprint && bssid == other.bssid
        override fun hashCode() = bssid.hashCode()
    }

    data class MatchResult(
        val bssid: String,
        val similarity: Float,
        val likelyMacRandomized: Boolean
    )

    // bssid -> list of (timestampMicros)
    private val timestampBuffer = mutableMapOf<String, MutableList<Long>>()
    private val MAX_BUFFER = 50

    fun addObservation(bssid: String, timestampMicros: Long) {
        val buf = timestampBuffer.getOrPut(bssid) { mutableListOf() }
        buf.add(timestampMicros)
        if (buf.size > MAX_BUFFER) buf.removeAt(0)
    }

    fun canCompute(bssid: String): Boolean =
        (timestampBuffer[bssid]?.size ?: 0) >= 8

    /**
     * Build fingerprint from buffered timestamps.
     */
    fun buildFingerprint(bssid: String): Fingerprint? {
        val ts = timestampBuffer[bssid] ?: return null
        if (ts.size < 8) return null

        val sortedTs = ts.sorted()
        // Inter-arrival times in ms
        val iat = (1 until sortedTs.size).map {
            (sortedTs[it] - sortedTs[it - 1]).toFloat() / 1000f
        }

        val mean = iat.average().toFloat()
        val variance = iat.map { (it - mean).pow(2) }.average().toFloat()
        val stdDev = sqrt(variance)
        val skewness = if (stdDev < 1e-6f) 0f else
            iat.map { ((it - mean) / stdDev).pow(3) }.average().toFloat()
        val kurtosis = if (stdDev < 1e-6f) 0f else
            iat.map { ((it - mean) / stdDev).pow(4) }.average().toFloat() - 3f

        // Beacon interval: mode of rounded IAT
        val rounded = iat.map { (it / 100f).roundToInt() * 100 }
        val beaconInterval = rounded.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key?.toFloat() ?: mean

        // Clock drift: linear regression on timestamps vs expected
        val clockDrift = computeClockDrift(sortedTs, beaconInterval)

        // Probe burst: count bursts where IAT < 50ms
        val probeBurst = iat.count { it < 50f }

        // Spectral features (autocorrelation lags 1-5)
        val autocorr = (1..5).map { lag -> autocorrelation(iat, lag) }

        val feature = floatArrayOf(
            mean, variance, skewness, kurtosis,
            clockDrift, beaconInterval,
            probeBurst.toFloat(),
            autocorr[0], autocorr[1], autocorr[2], autocorr[3], autocorr[4]
        )

        return Fingerprint(bssid, feature, beaconInterval, clockDrift, false)
    }

    private fun computeClockDrift(timestamps: List<Long>, nominalIntervalMs: Float): Float {
        if (timestamps.size < 3 || nominalIntervalMs < 1f) return 0f
        val n = timestamps.size
        val xs = (0 until n).map { it.toFloat() }
        val ys = timestamps.map { it.toFloat() / 1000f } // ms
        val sumX = xs.sum(); val sumY = ys.sum()
        val sumXY = xs.zip(ys).sumOf { (x, y) -> (x * y).toDouble() }.toFloat()
        val sumX2 = xs.sumOf { (it * it).toDouble() }.toFloat()
        val cnt = n.toFloat()
        val denom = cnt * sumX2 - sumX * sumX
        if (abs(denom) < 1e-6f) return 0f
        val slope = (cnt * sumXY - sumX * sumY) / denom
        // drift in ppm = (actual_interval - nominal) / nominal * 1e6
        return ((slope - nominalIntervalMs) / nominalIntervalMs * 1e6f).coerceIn(-500f, 500f)
    }

    private fun autocorrelation(series: List<Float>, lag: Int): Float {
        if (series.size <= lag) return 0f
        val mean = series.average().toFloat()
        val denom = series.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat()
        if (denom < 1e-6f) return 0f
        val num = (0 until series.size - lag).sumOf { i ->
            ((series[i] - mean) * (series[i + lag] - mean)).toDouble()
        }.toFloat()
        return (num / denom).coerceIn(-1f, 1f)
    }

    /**
     * Cosine similarity between two feature vectors.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vector size mismatch" }
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom < 1e-6f) 0f else (dot / denom).coerceIn(-1f, 1f)
    }

    /**
     * Given a new fingerprint and a list of known fingerprints,
     * find best match. Detects MAC randomization if similarity > 0.92 but BSSID differs.
     */
    fun findMatch(
        newFp: Fingerprint,
        knownFingerprints: List<Fingerprint>,
        threshold: Float = 0.92f
    ): MatchResult? {
        var bestSim = 0f
        var bestFp: Fingerprint? = null
        for (known in knownFingerprints) {
            val sim = cosineSimilarity(newFp.featureVector, known.featureVector)
            if (sim > bestSim) { bestSim = sim; bestFp = known }
        }
        if (bestFp == null || bestSim < threshold) return null
        val macRandomized = bestSim > threshold && bestFp.bssid != newFp.bssid
        return MatchResult(bestFp.bssid, bestSim, macRandomized)
    }

    fun clearBuffer(bssid: String) = timestampBuffer.remove(bssid)
    fun clearAll() = timestampBuffer.clear()

    private fun Float.pow(n: Int): Float {
        var r = 1f; repeat(n) { r *= this }; return r
    }
    private fun Float.roundToInt(): Int = Math.round(this)
}
