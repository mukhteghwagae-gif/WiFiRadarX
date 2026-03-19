package com.wifiradarx.app.intelligence

import kotlin.math.*
import java.util.*

/**
 * EMF Fingerprinter: collect ScanResult.timestampMicros, build IAT series, extract 12-dim feature vector
 */
class EmfFingerprinter {
    data class FeatureVector(
        val mean: Double,
        val variance: Double,
        val skewness: Double,
        val kurtosis: Double,
        val clockDrift: Double,
        val beaconInterval: Double,
        val probeBurst: Double,
        val iatMean: Double,
        val iatVar: Double,
        val rssiMean: Double,
        val rssiVar: Double,
        val timestampSkew: Double
    )

    fun extractFeatures(timestamps: List<Long>, rssis: List<Int>): FeatureVector {
        val iats = timestamps.zipWithNext { a, b -> (b - a).toDouble() }
        val iatMean = iats.average()
        val iatVar = iats.map { (it - iatMean).pow(2) }.average()
        
        val rssiMean = rssis.map { it.toDouble() }.average()
        val rssiVar = rssis.map { (it.toDouble() - rssiMean).pow(2) }.average()
        
        // Simplified skewness and kurtosis
        val skewness = iats.map { (it - iatMean).pow(3) }.average() / iatVar.pow(1.5)
        val kurtosis = iats.map { (it - iatMean).pow(4) }.average() / iatVar.pow(2)
        
        return FeatureVector(
            mean = iatMean, variance = iatVar, skewness = skewness, kurtosis = kurtosis,
            clockDrift = 0.0, beaconInterval = 102.4, probeBurst = 0.0,
            iatMean = iatMean, iatVar = iatVar, rssiMean = rssiMean, rssiVar = rssiVar,
            timestampSkew = skewness
        )
    }

    fun cosineSimilarity(v1: FeatureVector, v2: FeatureVector): Double {
        val a = doubleArrayOf(v1.mean, v1.variance, v1.skewness, v1.kurtosis, v1.iatMean, v1.iatVar, v1.rssiMean, v1.rssiVar)
        val b = doubleArrayOf(v2.mean, v2.variance, v2.skewness, v2.kurtosis, v2.iatMean, v2.iatVar, v2.rssiMean, v2.rssiVar)
        
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i].pow(2)
            normB += b[i].pow(2)
        }
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
}

/**
 * Rogue AP Detector: threat score 0–100
 */
class RogueApDetector {
    fun calculateThreatScore(
        ssidMismatch: Boolean,
        ouiMismatch: Boolean,
        securityChanged: Boolean,
        channelChanged: Boolean,
        directionUnexpected: Boolean,
        wpsAppeared: Boolean
    ): Int {
        var score = 0
        if (ssidMismatch) score += 40
        if (ouiMismatch) score += 20
        if (securityChanged) score += 15
        if (channelChanged) score += 10
        if (directionUnexpected) score += 15
        if (wpsAppeared) score += 10
        return score.coerceIn(0, 100)
    }
}

/**
 * Simulated Annealing Optimizer: multi-AP channel assignment
 */
class SimulatedAnnealingOptimizer(
    private val tInit: Double = 1000.0,
    private val cooling: Double = 0.995,
    private val iterations: Int = 5000
) {
    fun optimize(apCount: Int, channels: List<Int>): IntArray {
        var currentSolution = IntArray(apCount) { channels.random() }
        var currentEnergy = calculateEnergy(currentSolution)
        var bestSolution = currentSolution.copyOf()
        var bestEnergy = currentEnergy
        
        var t = tInit
        repeat(iterations) {
            val nextSolution = currentSolution.copyOf()
            nextSolution[Random().nextInt(apCount)] = channels.random()
            val nextEnergy = calculateEnergy(nextSolution)
            
            if (nextEnergy < currentEnergy || exp((currentEnergy - nextEnergy) / t) > Random().nextDouble()) {
                currentSolution = nextSolution
                currentEnergy = nextEnergy
            }
            
            if (currentEnergy < bestEnergy) {
                bestSolution = currentSolution.copyOf()
                bestEnergy = currentEnergy
            }
            t *= cooling
        }
        return bestSolution
    }

    private fun calculateEnergy(solution: IntArray): Double {
        var energy = 0.0
        for (i in solution.indices) {
            for (j in i + 1 until solution.size) {
                val diff = abs(solution[i] - solution[j])
                if (diff < 5) energy += (5 - diff).toDouble().pow(2)
            }
        }
        return energy
    }
}

/**
 * Interference Triangulator: gradient descent to find source
 */
class InterferenceTriangulator {
    data class Sample(val x: Float, val z: Float, val score: Float)

    fun triangulate(samples: List<Sample>): Pair<Float, Float> {
        if (samples.isEmpty()) return Pair(0f, 0f)
        
        var bestX = samples.map { it.x }.average().toFloat()
        var bestZ = samples.map { it.z }.average().toFloat()
        
        val learningRate = 0.1f
        repeat(100) {
            var gradX = 0f
            var gradZ = 0f
            for (s in samples) {
                val dist = sqrt((bestX - s.x).pow(2) + (bestZ - s.z).pow(2))
                val weight = s.score / (dist + 0.1f)
                gradX += weight * (bestX - s.x)
                gradZ += weight * (bestZ - s.z)
            }
            bestX -= learningRate * gradX
            bestZ -= learningRate * gradZ
        }
        return Pair(bestX, bestZ)
    }
}
