package com.wifiradarx.app.intelligence

import kotlin.math.*

/**
 * Inverse-Distance Weighting interpolator.
 * Pure Kotlin, no Android dependencies.
 */
class IdwInterpolator(private val power: Double = 2.0) {

    data class Sample(val x: Float, val y: Float, val z: Float, val value: Float)
    data class GridCell(val x: Float, val y: Float, val value: Float)
    data class VoxelCell(val x: Float, val y: Float, val z: Float, val value: Float)

    private val samples = mutableListOf<Sample>()

    fun addSample(x: Float, y: Float, value: Float, z: Float = 0f) {
        samples.add(Sample(x, y, z, value))
    }

    fun addSamples(newSamples: List<Sample>) {
        samples.addAll(newSamples)
    }

    fun clearSamples() = samples.clear()

    fun getSampleCount() = samples.size

    /** Predict value at 2D point (x, y) using all samples projected to XY plane. */
    fun predict(x: Float, y: Float): Float {
        if (samples.isEmpty()) return Float.NaN

        var weightedSum = 0.0
        var totalWeight = 0.0

        for (s in samples) {
            val dist = sqrt(((x - s.x) * (x - s.x) + (y - s.y) * (y - s.y)).toDouble())
            if (dist < 1e-6) return s.value
            val w = 1.0 / dist.pow(power)
            weightedSum += w * s.value
            totalWeight += w
        }

        return if (totalWeight < 1e-12) Float.NaN else (weightedSum / totalWeight).toFloat()
    }

    /** Predict value at 3D point. */
    fun predict3D(x: Float, y: Float, z: Float): Float {
        if (samples.isEmpty()) return Float.NaN

        var weightedSum = 0.0
        var totalWeight = 0.0

        for (s in samples) {
            val dx = (x - s.x).toDouble()
            val dy = (y - s.y).toDouble()
            val dz = (z - s.z).toDouble()
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist < 1e-6) return s.value
            val w = 1.0 / dist.pow(power)
            weightedSum += w * s.value
            totalWeight += w
        }

        return if (totalWeight < 1e-12) Float.NaN else (weightedSum / totalWeight).toFloat()
    }

    /**
     * Build a 2D grid of interpolated values.
     * @param gridSize number of cells per axis
     */
    fun buildGrid(
        xMin: Float, xMax: Float,
        yMin: Float, yMax: Float,
        gridSize: Int = 32
    ): Array<FloatArray> {
        val grid = Array(gridSize) { FloatArray(gridSize) }
        val xStep = (xMax - xMin) / gridSize
        val yStep = (yMax - yMin) / gridSize

        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                val x = xMin + i * xStep + xStep / 2
                val y = yMin + j * yStep + yStep / 2
                grid[i][j] = predict(x, y)
            }
        }
        return grid
    }

    /** Build list of grid cells with coordinates for rendering. */
    fun buildGridCells(
        xMin: Float, xMax: Float,
        yMin: Float, yMax: Float,
        gridSize: Int = 32
    ): List<GridCell> {
        val cells = mutableListOf<GridCell>()
        val xStep = (xMax - xMin) / gridSize
        val yStep = (yMax - yMin) / gridSize

        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                val x = xMin + i * xStep + xStep / 2
                val y = yMin + j * yStep + yStep / 2
                val v = predict(x, y)
                if (!v.isNaN()) cells.add(GridCell(x, y, v))
            }
        }
        return cells
    }

    /** Build volumetric voxel grid at a specific Y (height) slice. */
    fun buildVoxelSlice(
        xMin: Float, xMax: Float,
        zMin: Float, zMax: Float,
        ySlice: Float,
        gridSize: Int = 16
    ): List<VoxelCell> {
        val cells = mutableListOf<VoxelCell>()
        val xStep = (xMax - xMin) / gridSize
        val zStep = (zMax - zMin) / gridSize

        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                val x = xMin + i * xStep + xStep / 2
                val z = zMin + j * zStep + zStep / 2
                val v = predict3D(x, ySlice, z)
                if (!v.isNaN()) cells.add(VoxelCell(x, ySlice, z, v))
            }
        }
        return cells
    }

    /** Compute bounding box of all samples. */
    fun getBounds(): FloatArray? {
        if (samples.isEmpty()) return null
        var xMin = Float.MAX_VALUE; var xMax = Float.MIN_VALUE
        var yMin = Float.MAX_VALUE; var yMax = Float.MIN_VALUE
        for (s in samples) {
            xMin = minOf(xMin, s.x); xMax = maxOf(xMax, s.x)
            yMin = minOf(yMin, s.y); yMax = maxOf(yMax, s.y)
        }
        val pad = 1f
        return floatArrayOf(xMin - pad, xMax + pad, yMin - pad, yMax + pad)
    }
}
