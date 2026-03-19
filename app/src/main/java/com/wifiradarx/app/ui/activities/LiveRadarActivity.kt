package com.wifiradarx.app.ui.activities

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wifiradarx.app.R
import com.wifiradarx.app.ui.viewmodel.MainViewModel
import com.wifiradarx.app.ui.views.RadarView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LiveRadarActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()
    private lateinit var radarView: RadarView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_radar)
        supportActionBar?.title = "Live Radar"

        radarView = findViewById(R.id.radar_view)
        val tvCount = findViewById<TextView>(R.id.tv_radar_count)
        val btnScan = findViewById<Button>(R.id.btn_radar_scan)

        lifecycleScope.launch {
            vm.scanResults.collectLatest { results ->
                val blips = results.mapIndexed { i, sr ->
                    RadarView.RadarBlip(
                        bssid    = sr.BSSID ?: "",
                        ssid     = sr.SSID  ?: "",
                        rssi     = sr.level,
                        angleDeg = (i * 360f / results.size.coerceAtLeast(1)) % 360f
                    )
                }
                radarView.blips = blips
                tvCount?.text   = "${results.size} networks detected"
            }
        }

        btnScan?.setOnClickListener { vm.triggerScan() }
    }
}
