package com.wifiradarx.app.ui.activities

import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.wifiradarx.app.R
import com.wifiradarx.app.data.entity.WifiScanResult
import com.wifiradarx.app.ui.viewmodel.AnalyticsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AnalyticsDashboardActivity : AppCompatActivity() {

    private val vm: AnalyticsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)
        supportActionBar?.title = "Analytics Dashboard"

        val lineChart = findViewById<LineChart>(R.id.line_chart_rssi)
        val barChart  = findViewById<BarChart>(R.id.bar_chart_dist)
        setupLineChart(lineChart)
        setupBarChart(barChart)

        lifecycleScope.launch {
            vm.allScans.collectLatest { scans ->
                if (scans.isEmpty()) return@collectLatest
                updateLineChart(lineChart, scans.takeLast(60).map { it.rssi.toFloat() })
                updateBarChart(barChart, scans)

                val best = scans.maxByOrNull { it.rssi }
                val worst = scans.minByOrNull { it.rssi }
                val avg  = scans.map { it.rssi }.average()

                findViewById<TextView>(R.id.tv_insight_best)?.text =
                    "Strongest: ${best?.ssid ?: "-"} (${best?.rssi ?: "-"} dBm)"
                findViewById<TextView>(R.id.tv_insight_worst)?.text =
                    "Weakest: ${worst?.ssid ?: "-"} (${worst?.rssi ?: "-"} dBm)"
                findViewById<TextView>(R.id.tv_insight_avg)?.text =
                    "Avg RSSI: ${"%.1f".format(avg)} dBm"
                findViewById<TextView>(R.id.tv_network_total)?.text =
                    "${scans.distinctBy { it.bssid }.size} unique APs"
            }
        }
    }

    private fun setupLineChart(chart: LineChart) {
        chart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            legend.textColor           = 0xFFCCCCCC.toInt()
            xAxis.position             = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor            = 0xFFAAAAAA.toInt()
            axisLeft.textColor         = 0xFFAAAAAA.toInt()
            axisRight.isEnabled        = false
            setBackgroundColor(0xFF0D1117.toInt())
        }
    }

    private fun updateLineChart(chart: LineChart, values: List<Float>) {
        val entries = values.mapIndexed { i, v -> Entry(i.toFloat(), v) }
        val ds = LineDataSet(entries, "RSSI (dBm)").apply {
            color          = 0xFF00D4FF.toInt()
            setCircleColor(0xFF00D4FF.toInt())
            lineWidth      = 2f
            circleRadius   = 2f
            valueTextColor = 0xFF888888.toInt()
            valueTextSize  = 8f
            mode           = LineDataSet.Mode.CUBIC_BEZIER
        }
        chart.data = LineData(ds)
        chart.invalidate()
    }

    private fun setupBarChart(chart: BarChart) {
        chart.apply {
            description.isEnabled = false
            legend.textColor      = 0xFFCCCCCC.toInt()
            xAxis.textColor       = 0xFFAAAAAA.toInt()
            xAxis.position        = XAxis.XAxisPosition.BOTTOM
            axisLeft.textColor    = 0xFFAAAAAA.toInt()
            axisRight.isEnabled   = false
            setBackgroundColor(0xFF0D1117.toInt())
        }
    }

    private fun updateBarChart(chart: BarChart, scans: List<WifiScanResult>) {
        val buckets = mutableMapOf<Int, Int>()
        for (s in scans) {
            val bucket = (s.rssi / 10) * 10
            buckets[bucket] = (buckets[bucket] ?: 0) + 1
        }
        val sorted  = buckets.entries.sortedBy { it.key }
        val entries = sorted.mapIndexed { i, e -> BarEntry(i.toFloat(), e.value.toFloat()) }
        val labels  = sorted.map { "${it.key}" }
        val ds = BarDataSet(entries, "Count").apply {
            colors         = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextColor = 0xFF888888.toInt()
        }
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.data = BarData(ds)
        chart.invalidate()
    }
}
