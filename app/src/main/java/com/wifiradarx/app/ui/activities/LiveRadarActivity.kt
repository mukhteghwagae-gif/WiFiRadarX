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

// FIX: Blip angles and distances now reflect actual signal characteristics.
//
// Angle assignment rules:
//   2.4 GHz  →  left semicircle   (180°–360°)
//   5 GHz    →  right semicircle  (0°–180°)
//   6 GHz    →  top quarter       (315°–360° / 0°–45°)
//
// Within each band the angle is subdivided by channel number so networks
// on different channels don't stack on top of each other.
//
// Distance from centre is proportional to signal weakness:
//   RSSI -30 dBm (strongest) → centre ring
//   RSSI -100 dBm (weakest)  → outer ring
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
                val blips = results.map { sr ->
                    // ── Band-based base angle ──────────────────────────────
                    val baseAngle = when {
                        sr.frequency in 6000..6999 -> 337.5f            // top
                        sr.frequency in 5000..5999 -> 45f               // right half starts at 45°
                        else                        -> 180f              // left half
                    }
                    // ── Channel subdivides within the band's sector ────────
                    val channelNorm = when {
                        sr.frequency in 6000..6999 ->
                            ((sr.frequency - 6000f) / 1200f).coerceIn(0f, 1f) * 45f
                        sr.frequency in 5000..5999 ->
                            ((sr.frequency - 5000f) / 1000f).coerceIn(0f, 1f) * 135f
                        else ->
                            ((sr.frequency - 2400f) / 100f).coerceIn(0f, 1f) * 180f
                    }
                    val angleDeg = (baseAngle + channelNorm) % 360f

                    RadarView.RadarBlip(
                        bssid    = sr.BSSID ?: "",
                        ssid     = sr.SSID  ?: "",
                        rssi     = sr.level,
                        angleDeg = angleDeg
                    )
                }
                radarView.blips = blips
                tvCount?.text   = "${results.size} networks detected"

                // Show band breakdown
                val n24 = results.count { it.frequency in 2400..2499 }
                val n5  = results.count { it.frequency in 5000..5999 }
                val n6  = results.count { it.frequency in 6000..6999 }
                val summary = buildString {
                    if (n24 > 0) append("2.4GHz:$n24  ")
                    if (n5  > 0) append("5GHz:$n5  ")
                    if (n6  > 0) append("6GHz:$n6")
                }
                tvCount?.append(if (summary.isNotEmpty()) "\n$summary" else "")
            }
        }

        // FIX: Also observe the insight report and show anomaly count
        lifecycleScope.launch {
            vm.insightReport.collect { report ->
                if (report != null && report.rogueAlerts.isNotEmpty()) {
                    tvCount?.append("\n⚠ ${report.rogueAlerts.size} rogue suspect(s)")
                }
            }
        }

        btnScan?.setOnClickListener { vm.triggerScan() }
    }
}
