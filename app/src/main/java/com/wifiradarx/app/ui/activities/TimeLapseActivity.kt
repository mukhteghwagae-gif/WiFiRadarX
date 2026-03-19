package com.wifiradarx.app.ui.activities

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.wifiradarx.app.databinding.ActivityTimeLapseBinding

class TimeLapseActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTimeLapseBinding
    private val handler = Handler(Looper.getMainLooper())
    private val rssiHistory = mutableListOf<Entry>()
    private var tick = 0
    private var isPlaying = false

    private val playbackRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                addDataPoint()
                handler.postDelayed(this, 500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimeLapseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Time-Lapse Replay"
        }

        setupChart()

        binding.playPauseButton.setOnClickListener {
            isPlaying = !isPlaying
            binding.playPauseButton.text = if (isPlaying) "⏸ Pause" else "▶ Play"
            if (isPlaying) handler.post(playbackRunnable)
        }

        binding.resetButton.setOnClickListener {
            isPlaying = false
            binding.playPauseButton.text = "▶ Play"
            handler.removeCallbacks(playbackRunnable)
            tick = 0
            rssiHistory.clear()
            binding.timeLapseChart.data?.clearValues()
            binding.timeLapseChart.invalidate()
        }
    }

    private fun setupChart() {
        binding.timeLapseChart.apply {
            description.isEnabled = false
            setBackgroundColor(Color.parseColor("#12141D"))
            xAxis.apply {
                textColor = Color.WHITE
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
            }
            axisLeft.apply {
                textColor = Color.WHITE
                axisMinimum = -100f
                axisMaximum = -20f
            }
            axisRight.isEnabled = false
            legend.textColor = Color.WHITE
        }
    }

    private fun addDataPoint() {
        val simRssi = -65f + (Math.sin(tick * 0.3) * 10).toFloat() +
                (-5..5).random().toFloat()
        rssiHistory.add(Entry(tick.toFloat(), simRssi))
        tick++

        if (rssiHistory.size > 60) rssiHistory.removeAt(0)

        val dataSet = LineDataSet(rssiHistory.toMutableList(), "RSSI dBm").apply {
            color = Color.parseColor("#00D4FF")
            setCircleColor(Color.parseColor("#00D4FF"))
            valueTextColor = Color.WHITE
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        binding.timeLapseChart.data = LineData(dataSet)
        binding.timeLapseChart.notifyDataSetChanged()
        binding.timeLapseChart.invalidate()
        binding.currentRssiText.text = "Current: ${String.format("%.1f", simRssi)} dBm"
    }

    override fun onPause() {
        super.onPause()
        isPlaying = false
        handler.removeCallbacks(playbackRunnable)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
