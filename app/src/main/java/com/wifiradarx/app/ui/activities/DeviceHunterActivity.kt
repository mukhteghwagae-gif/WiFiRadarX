package com.wifiradarx.app.ui.activities

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wifiradarx.app.R
import com.wifiradarx.app.ui.viewmodel.DeviceHunterViewModel
import com.wifiradarx.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeviceHunterActivity : AppCompatActivity() {

    private val vm     : DeviceHunterViewModel by viewModels()
    private val mainVm : MainViewModel         by viewModels()

    private var walkX = 0f
    private var walkZ = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_hunter)
        supportActionBar?.title = "Device Hunter"

        val tvSource = findViewById<TextView>(R.id.tv_source_info)
        val tvCount  = findViewById<TextView>(R.id.tv_measurement_count)
        val btnReset = findViewById<Button>(R.id.btn_reset_hunt)

        // Auto-collect measurements every 2 s as user "walks"
        lifecycleScope.launch {
            while (isActive) {
                val results = mainVm.scanResults.value
                if (results.isNotEmpty()) {
                    val maxRssi = results.maxOf { it.level }.toFloat()
                    val score   = (maxRssi + 100f).coerceIn(0f, 100f)
                    vm.addMeasurement(walkX, walkZ, score)
                    walkX += 0.5f
                }
                delay(2_000L)
            }
        }

        lifecycleScope.launch {
            vm.source.collect { src ->
                tvSource?.text = if (src != null) {
                    "Source: ${src.type.displayName}\n" +
                    "Location: (${"%.1f".format(src.x)}, ${"%.1f".format(src.z)})\n" +
                    "Confidence: ${(src.confidence * 100).toInt()}%"
                } else {
                    "Collecting measurements…"
                }
            }
        }

        lifecycleScope.launch {
            vm.measurementCount.collect { count ->
                tvCount?.text = "$count measurements collected"
            }
        }

        btnReset?.setOnClickListener {
            vm.reset()
            walkX = 0f
            walkZ = 0f
        }
    }
}
