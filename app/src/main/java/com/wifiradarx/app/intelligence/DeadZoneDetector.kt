package com.wifiradarx.app.intelligence

import kotlin.math.sqrt

/**
 * Detects dead zones (weak signal areas) via flood-fill clustering on IDW grid.
 * Pure Kotlin, no Android deps.
 */
class DeadZoneDetector(
    private val weakThresholdDbm: Float = -75f,
    private val deadThresholdDbm: Float = -85f
) {

    data class DeadZone(
        val cells: List<Pair<Int, Int>>,      // (row, col) indices
        val centroidX: Float,
        val centroidY: Float,
        val areaCells: Int,
        val avgSignal: Float,
        val severity: Severity
    )

    data class ApRecommendation(
        val x: Float,
        val y: Float,
        val priority: Int,  // 1 = highest
        val coveredZones: Int,
        val reason: String
    )

    enum class Severity { WEAK, DEAD }

    /**
     * Find dead zones in a 2D signal grid.
     * @param grid grid[row][col] = rssi value (Float.NaN = no data)
     * @param xMin, xMax, yMin, yMax: real-world bounds
     */
    fun findDeadZones(
        grid: Array<FloatArray>,
        xMin: Float, xMax: Float,
        yMin: Float, yMax: Float
    ): List<DeadZone> {
        val rows = grid.size
        if (rows == 0) return emptyList()
        val cols = grid[0].size
        val visited = Array(rows) { BooleanArray(cols) }
        val zones = mutableListOf<DeadZone>()

        val xStep = (xMax - xMin) / cols
        val yStep = (yMax - yMin) / rows

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val v = grid[r][c]
                if (!visited[r][c] && !v.isNaN() && v < weakThresholdDbm) {
                    val cluster = floodFill(grid, visited, r, c, rows, cols)
                    if (cluster.size >= 4) { // min cluster size
                        val avgSig = cluster.map { (cr, cc) -> grid[cr][cc] }.average().toFloat()
                        val centR = cluster.map { it.first }.average().toFloat()
                        val centC = cluster.map { it.second }.average().toFloat()
                        val centX = xMin + centC * xStep + xStep / 2
                        val centY = yMin + centR * yStep + yStep / 2
                        val severity = if (avgSig < deadThresholdDbm) Severity.DEAD else Severity.WEAK
                        zones.add(DeadZone(cluster, centX, centY, cluster.size, avgSig, severity))
                    }
                }
            }
        }
        return zones.sortedByDescending { it.areaCells }
    }

    private fun floodFill(
        grid: Array<FloatArray>,
        visited: Array<BooleanArray>,
        startR: Int, startC: Int,
        rows: Int, cols: Int
    ): List<Pair<Int, Int>> {
        val cluster = mutableListOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(startR to startC)
        visited[startR][startC] = true

        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            cluster.add(r to c)
            for ((dr, dc) in dirs) {
                val nr = r + dr; val nc = c + dc
                if (nr in 0 until rows && nc in 0 until cols
                    && !visited[nr][nc]
                    && !grid[nr][nc].isNaN()
                    && grid[nr][nc] < weakThresholdDbm
                ) {
                    visited[nr][nc] = true
                    queue.add(nr to nc)
                }
            }
        }
        return cluster
    }

    /**
     * Given dead zones, recommend AP placements.
     * Strategy: for each dead zone, recommend centroid.
     * Merge zones that are close together.
     */
    fun recommendApPlacements(
        zones: List<DeadZone>,
        mergeRadiusM: Float = 3f
    ): List<ApRecommendation> {
        if (zones.isEmpty()) return emptyList()

        val merged = mutableListOf<MutableList<DeadZone>>()

        for (zone in zones) {
            val group = merged.firstOrNull { g ->
                g.any { z ->
                    dist(zone.centroidX, zone.centroidY, z.centroidX, z.centroidY) < mergeRadiusM
                }
            }
            if (group != null) group.add(zone) else merged.add(mutableListOf(zone))
        }

        return merged.mapIndexed { i, group ->
            val cx = group.map { it.centroidX }.average().toFloat()
            val cy = group.map { it.centroidY }.average().toFloat()
            val area = group.sumOf { it.areaCells }
            val deadCount = group.count { it.severity == Severity.DEAD }
            ApRecommendation(
                x = cx,
                y = cy,
                priority = i + 1,
                coveredZones = group.size,
                reason = "Covers ${group.size} zone(s), $deadCount dead. Total area: $area cells"
            )
        }.sortedBy { it.priority }
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }
}
