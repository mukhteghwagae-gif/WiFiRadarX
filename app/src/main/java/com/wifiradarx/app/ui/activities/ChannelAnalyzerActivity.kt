package com.wifiradarx.app.ui.activities

import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.wifiradarx.app.R
import com.wifiradarx.app.intelligence.ChannelAnalyzer
import com.wifiradarx.app.ui.viewmodel.AnalyticsViewModel
import com.wifiradarx.app.ui.viewmodel.ChannelViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChannelAnalyzerActivity : AppCompatActivity() {

    private val vm          : ChannelViewModel   by viewModels()
    private val analyticsVm : AnalyticsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_analyzer)
        supportActionBar?.title = "Channel Analyzer"

        val barChart24 = findViewById<BarChart>(R.id.bar_chart_24)
        val barChart5  = findViewById<BarChart>(R.id.bar_chart_5)
        setupChart(barChart24, "2.4 GHz")
        setupChart(barChart5,  "5 GHz")

        // Feed scans → analyzer
        lifecycleScope.launch {
            analyticsVm.allScans.collectLatest { scans -> vm.analyze(scans) }
        }

        lifecycleScope.launch {
            vm.channelStats.collect { stats ->
                updateChart(barChart24, stats, ChannelAnalyzer.Band.GHZ_2_4)
                updateChart(barChart5,  stats, ChannelAnalyzer.Band.GHZ_5)
            }
        }

        lifecycleScope.launch {
            vm.best24.collect { recs ->
                findViewById<TextView>(R.id.tv_best_24)?.text =
                    recs.joinToString("\n") { "Ch ${it.channel}: ${it.reason}" }
            }
        }

        lifecycleScope.launch {
            vm.best5.collect { recs ->
                findViewById<TextView>(R.id.tv_best_5)?.text =
                    recs.joinToString("\n") { "Ch ${it.channel}: ${it.reason}" }
            }
        }

        lifecycleScope.launch {
            vm.optimizedChannels.collect { nodes ->
                val tv = findViewById<TextView>(R.id.tv_sa_result)
                tv?.text = if (nodes.isEmpty()) "Run a scan first"
                else nodes.joinToString("\n") { "AP ${it.id.takeLast(5)} → Ch ${it.assignedChannel}" }
            }
        }
    }

    private fun setupChart(chart: BarChart, label: String) {
        chart.apply {
            description.text      = label
            description.textColor = 0xFF00D4FF.toInt()
            description.textSize  = 12f
            setBackgroundColor(0xFF0D1117.toInt())
            xAxis.textColor       = 0xFFAAAAAA.toInt()
            xAxis.position        = XAxis.XAxisPosition.BOTTOM
            axisLeft.textColor    = 0xFFAAAAAA.toInt()
            axisRight.isEnabled   = false
            legend.textColor      = 0xFFCCCCCC.toInt()
        }
    }

    private fun updateChart(
        chart: BarChart,
        stats: Map<Int, ChannelAnalyzer.ChannelStats>,
        band: ChannelAnalyzer.Band
    ) {
        val filtered = stats.values.filter { it.band == band }.sortedBy { it.channel }
        if (filtered.isEmpty()) return
        val entries = filtered.mapIndexed { i, s -> BarEntry(i.toFloat(), s.congestionScore) }
        val labels  = filtered.map { "Ch${it.channel}" }
        val ds = BarDataSet(entries, "Congestion %").apply {
            colors = filtered.map { s ->
                when {
                    s.congestionScore < 30 -> 0xFF00FF88.toInt()
                    s.congestionScore < 60 -> 0xFFFFCC00.toInt()
                    else                   -> 0xFFFF4444.toInt()
                }
            }
            valueTextColor = 0xFF888888.toInt()
        }
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.data = BarData(ds)
        chart.invalidate()
    }
}
