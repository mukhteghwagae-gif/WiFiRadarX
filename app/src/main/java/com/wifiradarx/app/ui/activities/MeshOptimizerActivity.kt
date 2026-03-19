package com.wifiradarx.app.ui.activities

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.wifiradarx.app.databinding.ActivityMeshOptimizerBinding
import com.wifiradarx.app.intelligence.SimulatedAnnealingOptimizer

class MeshOptimizerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMeshOptimizerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeshOptimizerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Mesh Optimizer"
        }

        runOptimization()
        setupRadarChart()
    }

    private fun runOptimization() {
        val optimizer = SimulatedAnnealingOptimizer()
        val channels = listOf(1, 6, 11)
        val result = optimizer.optimize(4, channels)
        binding.optimizationResultText.text =
            "Optimal channels: AP1→${result[0]}, AP2→${result[1]}, AP3→${result[2]}, AP4→${result[3]}"
        binding.optimizationSubText.text = "Interference minimized via simulated annealing"
    }

    private fun setupRadarChart() {
        val coverageEntries = listOf(
            RadarEntry(0.9f), RadarEntry(0.75f), RadarEntry(0.85f),
            RadarEntry(0.6f), RadarEntry(0.8f), RadarEntry(0.7f)
        )
        val deadZoneEntries = listOf(
            RadarEntry(0.1f), RadarEntry(0.25f), RadarEntry(0.15f),
            RadarEntry(0.4f), RadarEntry(0.2f), RadarEntry(0.3f)
        )

        val coverageSet = RadarDataSet(coverageEntries, "Coverage").apply {
            color = Color.parseColor("#00D4FF")
            fillColor = Color.parseColor("#4400D4FF")
            setDrawFilled(true)
            valueTextColor = Color.WHITE
        }
        val deadZoneSet = RadarDataSet(deadZoneEntries, "Dead Zones").apply {
            color = Color.parseColor("#FF4B4B")
            fillColor = Color.parseColor("#44FF4B4B")
            setDrawFilled(true)
            valueTextColor = Color.WHITE
        }

        binding.meshRadarChart.apply {
            data = RadarData(coverageSet, deadZoneSet)
            description.isEnabled = false
            setBackgroundColor(Color.parseColor("#12141D"))
            yAxis.textColor = Color.WHITE
            xAxis.textColor = Color.WHITE
            legend.textColor = Color.WHITE
            webColor = Color.parseColor("#33FFFFFF")
            webColorInner = Color.parseColor("#22FFFFFF")
            animateXY(800, 800)
            invalidate()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
