package com.wifiradarx.app.ui.activities

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.wifiradarx.app.databinding.ActivityChannelAnalyzerBinding
import com.wifiradarx.app.intelligence.ChannelAnalyzer

class ChannelAnalyzerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChannelAnalyzerBinding
    private val analyzer = ChannelAnalyzer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChannelAnalyzerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Channel Analyzer"
        }

        // Simulate scan results with channel → rssi pairs
        val mockScan = listOf(
            1 to -65, 1 to -70, 6 to -55, 6 to -60, 6 to -58,
            11 to -72, 11 to -68, 3 to -80, 9 to -75
        )
        val scores = analyzer.analyze(mockScan)
        setupBarChart(scores)

        val best = scores.firstOrNull()
        binding.bestChannelText.text = "Best Channel: ${best?.channel ?: "--"}"
        binding.bestChannelScore.text = "Score: ${String.format("%.0f%%", (best?.totalScore ?: 0.0) * 100)}"
    }

    private fun setupBarChart(scores: List<ChannelAnalyzer.ChannelScore>) {
        val entries = scores.take(14).mapIndexed { idx, cs ->
            BarEntry(idx.toFloat(), cs.totalScore.toFloat())
        }
        val labels = scores.take(14).map { "Ch${it.channel}" }

        val dataSet = BarDataSet(entries, "Channel Score").apply {
            colors = entries.map { e ->
                when {
                    e.y > 0.7f -> Color.parseColor("#4BFF4B")
                    e.y > 0.4f -> Color.parseColor("#FFB84B")
                    else -> Color.parseColor("#FF4B4B")
                }
            }
            valueTextColor = Color.WHITE
            valueTextSize = 10f
        }

        binding.channelChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            setBackgroundColor(Color.parseColor("#12141D"))
            xAxis.apply {
                textColor = Color.WHITE
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                    override fun getFormattedValue(value: Float) =
                        labels.getOrElse(value.toInt()) { "" }
                }
                granularity = 1f
            }
            axisLeft.apply {
                textColor = Color.WHITE
                axisMinimum = 0f
                axisMaximum = 1f
            }
            axisRight.isEnabled = false
            legend.textColor = Color.WHITE
            animateY(600)
            invalidate()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
