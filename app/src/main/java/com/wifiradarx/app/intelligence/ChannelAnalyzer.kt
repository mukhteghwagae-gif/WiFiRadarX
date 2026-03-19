package com.wifiradarx.app.intelligence

import kotlin.math.abs

/**
 * Per-channel congestion analysis for 2.4 / 5 / 6 GHz bands.
 */
object ChannelAnalyzer {

    data class ChannelStats(
        val channel: Int,
        val band: Band,
        val frequency: Int,
        val networkCount: Int,
        val avgRssi: Float,
        val maxRssi: Float,
        val congestionScore: Float,  // 0-100, higher = more congested
        val interferenceScore: Float // 0-100
    )

    data class ChannelRecommendation(
        val channel: Int,
        val band: Band,
        val score: Float, // higher = better
        val reason: String
    )

    enum class Band { GHZ_2_4, GHZ_5, GHZ_6 }

    // 2.4 GHz non-overlapping channels
    private val NONOVERLAPPING_24 = setOf(1, 6, 11)
    // 5 GHz DFS-free channels (common)
    private val PREFERRED_5 = setOf(36, 40, 44, 48, 149, 153, 157, 161, 165)

    fun frequencyToChannel(freqMHz: Int): Int {
        return when {
            freqMHz == 2484 -> 14
            freqMHz in 2412..2472 -> (freqMHz - 2412) / 5 + 1
            freqMHz in 5170..5825 -> (freqMHz - 5000) / 5
            freqMHz in 5955..7115 -> (freqMHz - 5955) / 5 + 1
            else -> 0
        }
    }

    fun channelToFrequency(channel: Int, band: Band): Int {
        return when (band) {
            Band.GHZ_2_4 -> if (channel == 14) 2484 else 2412 + (channel - 1) * 5
            Band.GHZ_5 -> 5000 + channel * 5
            Band.GHZ_6 -> 5955 + (channel - 1) * 5
        }
    }

    fun getBand(freqMHz: Int): Band = when {
        freqMHz in 2400..2500 -> Band.GHZ_2_4
        freqMHz in 5100..5900 -> Band.GHZ_5
        freqMHz in 5925..7125 -> Band.GHZ_6
        else -> Band.GHZ_2_4
    }

    /**
     * Analyze channel usage from a list of (frequency, rssi) pairs.
     */
    fun analyzeChannels(
        networks: List<Pair<Int, Int>> // (frequency, rssi)
    ): Map<Int, ChannelStats> {
        val grouped = networks.groupBy { frequencyToChannel(it.first) }
        val result = mutableMapOf<Int, ChannelStats>()

        for ((ch, nets) in grouped) {
            if (ch == 0) continue
            val freq = nets.first().first
            val band = getBand(freq)
            val rssis = nets.map { it.second.toFloat() }
            val avgRssi = rssis.average().toFloat()
            val maxRssi = rssis.maxOrNull() ?: -100f

            // Congestion: number of networks weighted by signal strength
            val congestion = nets.size.toFloat() * 20f +
                    rssis.sumOf { maxOf(0f, it + 100f).toDouble() }.toFloat() / 100f
            val clamped = congestion.coerceIn(0f, 100f)

            // Interference from adjacent channels (2.4GHz)
            val adj = if (band == Band.GHZ_2_4) {
                networks.filter { (f, _) ->
                    val c = frequencyToChannel(f)
                    c != ch && abs(c - ch) in 1..4
                }.size.toFloat() * 15f
            } else 0f

            result[ch] = ChannelStats(
                channel = ch,
                band = band,
                frequency = freq,
                networkCount = nets.size,
                avgRssi = avgRssi,
                maxRssi = maxRssi,
                congestionScore = clamped,
                interferenceScore = adj.coerceIn(0f, 100f)
            )
        }
        return result
    }

    /**
     * Rank channels and return best recommendations.
     */
    fun getBestChannels(
        channelStats: Map<Int, ChannelStats>,
        band: Band,
        topN: Int = 3
    ): List<ChannelRecommendation> {
        val allChannels = when (band) {
            Band.GHZ_2_4 -> (1..13).toList()
            Band.GHZ_5 -> PREFERRED_5.toList()
            Band.GHZ_6 -> listOf(1, 5, 9, 13, 17, 21, 25, 29, 33, 37, 41, 45)
        }

        return allChannels.map { ch ->
            val stats = channelStats[ch]
            val congestion = stats?.congestionScore ?: 0f
            val interference = stats?.interferenceScore ?: 0f
            val netCount = stats?.networkCount ?: 0
            val score = 100f - congestion * 0.6f - interference * 0.4f
            val preferred = when (band) {
                Band.GHZ_2_4 -> ch in NONOVERLAPPING_24
                Band.GHZ_5 -> ch in PREFERRED_5
                Band.GHZ_6 -> true
            }
            val bonus = if (preferred && netCount == 0) 15f else 0f
            val reason = buildString {
                if (netCount == 0) append("Empty channel. ")
                else append("$netCount network(s). ")
                if (ch in NONOVERLAPPING_24 && band == Band.GHZ_2_4) append("Non-overlapping. ")
                if (ch in PREFERRED_5 && band == Band.GHZ_5) append("DFS-free. ")
                append("Congestion: ${congestion.toInt()}%")
            }
            ChannelRecommendation(ch, band, (score + bonus).coerceIn(0f, 100f), reason)
        }.sortedByDescending { it.score }.take(topN)
    }

    /** Compute per-band interference score from a snapshot. */
    fun computeInterferenceScore(networks: List<Pair<Int, Int>>): Map<Band, Float> {
        val stats = analyzeChannels(networks)
        val result = mutableMapOf<Band, Float>()
        for (band in Band.values()) {
            val bandStats = stats.values.filter { it.band == band }
            result[band] = if (bandStats.isEmpty()) 0f
            else bandStats.map { it.congestionScore + it.interferenceScore }.average().toFloat()
        }
        return result
    }
}
