package com.wifiradarx.app.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wifiradarx.app.R
import com.wifiradarx.app.ui.viewmodel.MainViewModel
import com.wifiradarx.app.workers.BackgroundMonitorWorker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    private val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(Manifest.permission.CAMERA)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            vm.startScanning()
        } else {
            Toast.makeText(this, "Location permission required for WiFi scanning", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupBottomNav()
        setupQuickActions()
        observeViewModel()
        checkAndRequestPermissions()
    }

    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_radar -> { /* already on main */ true }
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AnalyticsDashboardActivity::class.java))
                    true
                }
                R.id.nav_security -> {
                    startActivity(Intent(this, SecurityAuditActivity::class.java))
                    true
                }
                R.id.nav_channels -> {
                    startActivity(Intent(this, ChannelAnalyzerActivity::class.java))
                    true
                }
                R.id.nav_networks -> {
                    startActivity(Intent(this, NetworkListActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupQuickActions() {
        // Quick action cards
        mapOf(
            R.id.card_ar to ArMappingActivity::class.java,
            R.id.card_radar to LiveRadarActivity::class.java,
            R.id.card_heatmap to HeatmapActivity::class.java,
            R.id.card_mesh to MeshOptimizerActivity::class.java,
            R.id.card_hunter to DeviceHunterActivity::class.java,
            R.id.card_timelapse to TimeLapseActivity::class.java
        ).forEach { (viewId, actClass) ->
            findViewById<MaterialCardView>(viewId)?.setOnClickListener {
                startActivity(Intent(this, actClass))
            }
        }

        findViewById<View>(R.id.card_settings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // FAB: trigger manual scan
        findViewById<FloatingActionButton>(R.id.fab_scan)?.setOnClickListener {
            vm.triggerScan()
            Toast.makeText(this, "Scan triggered…", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            vm.scanResults.collect { results ->
                val tvCount = findViewById<TextView>(R.id.tv_network_count)
                val tvBest = findViewById<TextView>(R.id.tv_best_signal)
                val tvStatus = findViewById<TextView>(R.id.tv_scan_status)

                tvCount?.text = "${results.size} networks"
                val best = results.maxByOrNull { it.level }
                tvBest?.text = if (best != null) "Strongest: ${best.SSID} (${best.level} dBm)" else "No signal"
                tvStatus?.text = if (vm.isWifiEnabled()) "WiFi: ON" else "WiFi: OFF"
            }
        }
        lifecycleScope.launch {
            vm.rogues.collect { rogues ->
                val tvAlert = findViewById<TextView>(R.id.tv_rogue_alert)
                tvAlert?.visibility = if (rogues.isNotEmpty()) View.VISIBLE else View.GONE
                tvAlert?.text = "⚠ ${rogues.size} rogue AP suspect(s) detected"
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            vm.startScanning()
            BackgroundMonitorWorker.schedule(this)
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        vm.triggerScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        vm.stopScanning()
    }
}
