package com.wifiradarx.app.intelligence

import kotlin.math.*

/**
 * IDW with configurable power, predict() + buildGrid() + 3D variant
 */
class IdwInterpolator(private val power: Double = 2.0) {
    data class Point3D(val x: Float, val y: Float, val z: Float, val value: Float)

    fun predict(targetX: Float, targetY: Float, targetZ: Float, points: List<Point3D>): Float {
        if (points.isEmpty()) return -100f
        
        var numerator = 0.0
        var denominator = 0.0
        
        for (p in points) {
            val d = sqrt(((targetX - p.x).pow(2) + (targetY - p.y).pow(2) + (targetZ - p.z).pow(2)).toDouble())
            if (d < 0.001) return p.value
            
            val weight = 1.0 / d.pow(power)
            numerator += weight * p.value
            denominator += weight
        }
        
        return (numerator / denominator).toFloat()
    }

    fun buildGrid(
        minX: Float, maxX: Float, 
        minZ: Float, maxZ: Float, 
        y: Float, 
        resolution: Float, 
        points: List<Point3D>
    ): Array<FloatArray> {
        val rows = ((maxX - minX) / resolution).toInt() + 1
        val cols = ((maxZ - minZ) / resolution).toInt() + 1
        val grid = Array(rows) { FloatArray(cols) }
        
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                grid[i][j] = predict(minX + i * resolution, y, minZ + j * resolution, points)
            }
        }
        return grid
    }
}

/**
 * IEEE 802.11 log-distance path loss model, self-calibrating via least-squares, 5 presets
 */
class PathLossModel {
    enum class Environment(val n: Double, val l0: Double) {
        FREE_SPACE(2.0, -40.0),
        INDOOR_OFFICE(3.0, -35.0),
        INDOOR_HOME(2.8, -32.0),
        URBAN(3.5, -45.0),
        DENSE_URBAN(4.0, -50.0)
    }

    private var currentN: Double = Environment.INDOOR_OFFICE.n
    private var currentL0: Double = Environment.INDOOR_OFFICE.l0

    fun calculateDistance(rssi: Int): Double {
        // RSSI = L0 - 10 * n * log10(d)
        // log10(d) = (L0 - RSSI) / (10 * n)
        // d = 10 ^ ((L0 - RSSI) / (10 * n))
        return 10.0.pow((currentL0 - rssi) / (10.0 * currentN))
    }

    fun calibrate(samples: List<Pair<Double, Int>>) {
        if (samples.size < 2) return
        
        // Linear regression on: RSSI = L0 - 10*n*log10(d)
        // y = RSSI, x = 10*log10(d)
        // y = L0 - n*x
        val x = samples.map { 10.0 * log10(it.first) }
        val y = samples.map { it.second.toDouble() }
        
        val meanX = x.average()
        val meanY = y.average()
        
        var num = 0.0
        var den = 0.0
        for (i in x.indices) {
            num += (x[i] - meanX) * (y[i] - meanY)
            den += (x[i] - meanX).pow(2)
        }
        
        val slope = num / den // This is -n
        currentN = -slope
        currentL0 = meanY - slope * meanX
        
        // Clamp to realistic values
        currentN = currentN.coerceIn(1.5, 6.0)
    }
}

/**
 * Flood-fill clustering on IDW grid, centroid-based AP placement recommendations
 */
class DeadZoneDetector(private val threshold: Float = -75f) {
    fun detect(grid: Array<FloatArray>, resolution: Float, minX: Float, minZ: Float): List<Pair<Float, Float>> {
        val rows = grid.size
        val cols = grid[0].size
        val visited = Array(rows) { BooleanArray(cols) }
        val clusters = mutableListOf<List<Pair<Int, Int>>>()

        for (i in 0 until rows) {
            for (j in 0 until cols) {
                if (!visited[i][j] && grid[i][j] < threshold) {
                    val cluster = mutableListOf<Pair<Int, Int>>()
                    floodFill(grid, i, j, visited, cluster)
                    if (cluster.size > 5) clusters.add(cluster)
                }
            }
        }

        return clusters.map { cluster ->
            val avgI = cluster.map { it.first }.average()
            val avgJ = cluster.map { it.second }.average()
            Pair(minX + avgI.toFloat() * resolution, minZ + avgJ.toFloat() * resolution)
        }
    }

    private fun floodFill(grid: Array<FloatArray>, i: Int, j: Int, visited: Array<BooleanArray>, cluster: MutableList<Pair<Int, Int>>) {
        if (i < 0 || i >= grid.size || j < 0 || j >= grid[0].size || visited[i][j] || grid[i][j] >= threshold) return
        
        visited[i][j] = true
        cluster.add(Pair(i, j))
        
        floodFill(grid, i + 1, j, visited, cluster)
        floodFill(grid, i - 1, j, visited, cluster)
        floodFill(grid, i, j + 1, visited, cluster)
        floodFill(grid, i, j - 1, visited, cluster)
    }
}
