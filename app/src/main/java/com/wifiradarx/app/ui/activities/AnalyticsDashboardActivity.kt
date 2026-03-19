package com.wifiradarx.app.ui.activities

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.wifiradarx.app.databinding.ActivityAnalyticsDashboardBinding

class AnalyticsDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnalyticsDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Analytics"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setupRssiChart()
    }

    private fun setupRssiChart() {
        val entries = listOf(-72f, -68f, -65f, -70f, -62f, -58f, -60f, -63f).mapIndexed { i, v ->
            Entry(i.toFloat(), v)
        }
        val dataSet = LineDataSet(entries, "RSSI (dBm)").apply {
            color = Color.parseColor("#00D4FF")
            valueTextColor = Color.WHITE
            lineWidth = 2.5f
            circleRadius = 4f
            setCircleColor(Color.parseColor("#00D4FF"))
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        with(binding.rssiChart) {
            data = LineData(dataSet)
            description.isEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            axisRight.isEnabled = false
            xAxis.textColor = Color.WHITE
            axisLeft.textColor = Color.WHITE
            axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = "${value.toInt()} dBm"
            }
            legend.textColor = Color.WHITE
            setTouchEnabled(true)
            setPinchZoom(true)
            invalidate()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
