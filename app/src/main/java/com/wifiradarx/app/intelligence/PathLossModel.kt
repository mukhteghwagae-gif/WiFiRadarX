package com.wifiradarx.app.intelligence

import kotlin.math.*

/**
 * IEEE 802.11 log-distance path loss model.
 * Self-calibrating via least-squares regression on collected samples.
 */
class PathLossModel(preset: Preset = Preset.OFFICE) {

    enum class Preset(
        val txPowerDbm: Float,
        val pathLossExp: Float,
        val referenceDistanceM: Float,
        val shadowingStdDb: Float,
        val description: String
    ) {
        FREE_SPACE(20f, 2.0f, 1f, 4f, "Free Space"),
        OFFICE(20f, 3.0f, 1f, 7f, "Office Building"),
        HOME(20f, 2.8f, 1f, 6f, "Home/Apartment"),
        INDUSTRIAL(20f, 3.5f, 1f, 9f, "Industrial/Warehouse"),
        OUTDOOR(20f, 2.2f, 1f, 5f, "Outdoor Urban")
    }

    var txPower: Float = preset.txPowerDbm
    var n: Float = preset.pathLossExp
    var d0: Float = preset.referenceDistanceM
    private val calibrationSamples = mutableListOf<Pair<Float, Float>>() // (distance, rssi)

    /** Estimate RSSI at given distance in meters. */
    fun estimateRssi(distanceM: Float): Float {
        if (distanceM <= 0f) return txPower
        return txPower - 10f * n * log10(maxOf(distanceM, d0) / d0)
    }

    /** Estimate distance in meters from RSSI. */
    fun estimateDistance(rssi: Float): Float {
        val exp = (txPower - rssi) / (10f * n)
        return d0 * 10f.pow(exp)
    }

    /**
     * Add a calibration sample (measured RSSI at known distance).
     */
    fun addCalibrationSample(distanceM: Float, rssi: Float) {
        calibrationSamples.add(Pair(distanceM, rssi))
        if (calibrationSamples.size >= 5) recalibrate()
    }

    /**
     * Least-squares calibration: estimate txPower and n from samples.
     * Model: RSSI = txPower - 10*n*log10(d/d0)
     * Let x = log10(d/d0), y = txPower - RSSI
     * => y = 10*n*x => linear regression through origin for n, intercept for txPower
     */
    private fun recalibrate() {
        val xs = calibrationSamples.map { (d, _) -> log10(maxOf(d, d0) / d0) }
        val ys = calibrationSamples.map { (_, r) -> r }
        val xArr = xs.toFloatArray()
        val yArr = ys.toFloatArray()

        // Normal least squares: fit y = a + b*x where a=txPower, b=-10*n
        val cnt = xArr.size.toFloat()
        val sumX = xArr.sum()
        val sumY = yArr.sum()
        val sumXY = xArr.zip(yArr.toList()).sumOf { (x, y) -> (x * y).toDouble() }.toFloat()
        val sumX2 = xArr.sumOf { (it * it).toDouble() }.toFloat()

        val denom = cnt * sumX2 - sumX * sumX
        if (abs(denom) < 1e-6f) return

        val b = (cnt * sumXY - sumX * sumY) / denom
        val a = (sumY - b * sumX) / cnt

        txPower = a
        n = -b / 10f
        n = n.coerceIn(1.5f, 6f)
    }

    fun clearCalibration() = calibrationSamples.clear()
    fun getCalibrationCount() = calibrationSamples.size
}
