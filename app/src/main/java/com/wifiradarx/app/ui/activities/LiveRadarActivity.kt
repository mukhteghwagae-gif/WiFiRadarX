package com.wifiradarx.app.ui.activities

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.wifiradarx.app.databinding.ActivityLiveRadarBinding
import com.wifiradarx.app.ui.views.RadarView
import kotlin.math.abs

class LiveRadarActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLiveRadarBinding
    private lateinit var wifiManager: WifiManager
    private val handler = Handler(Looper.getMainLooper())
    private var scanCount = 0

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                processScanResults()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveRadarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Live Radar"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        binding.scanCountText.text = "Initializing…"
        startScanning()
    }

    private fun startScanning() {
        handler.post(object : Runnable {
            override fun run() {
                if (ActivityCompat.checkSelfPermission(
                        this@LiveRadarActivity, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    @Suppress("DEPRECATION")
                    wifiManager.startScan()
                }
                handler.postDelayed(this, 3000)
            }
        })
    }

    private fun processScanResults() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        scanCount++
        val results = wifiManager.scanResults ?: return
        binding.scanCountText.text = "Scan #$scanCount — ${results.size} networks"

        val blips = results.take(20).mapIndexed { idx, result ->
            val angle = (idx * (360f / results.size.coerceAtLeast(1))) % 360f
            val dist = ((abs(result.level) - 30f) / 70f).coerceIn(0.1f, 1.0f)
            RadarView.Blip(
                angle = angle,
                distance = dist,
                rssi = result.level,
                label = result.SSID.take(12).ifBlank { "<hidden>" }
            )
        }
        binding.radarView.updateBlips(blips)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(wifiReceiver) } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
