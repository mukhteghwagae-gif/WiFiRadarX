package com.wifiradarx.app.ui.activities

import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.wifiradarx.app.R
import com.wifiradarx.app.WiFiRadarXApplication
import com.wifiradarx.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    data class ActionItem(val icon: String, val title: String, val subtitle: String, val targetClass: Class<*>)

    private val actions = listOf(
        ActionItem("📡", "AR Mapping",     "3D signal overlay",  ArMappingActivity::class.java),
        ActionItem("🔴", "Live Radar",     "Real-time sweep",    LiveRadarActivity::class.java),
        ActionItem("📊", "Analytics",      "Signal history",     AnalyticsDashboardActivity::class.java),
        ActionItem("🔒", "Security Audit", "Network threats",    SecurityAuditActivity::class.java),
        ActionItem("📶", "Channels",       "Congestion map",     ChannelAnalyzerActivity::class.java),
        ActionItem("🔍", "Device Hunter",  "Find devices",       DeviceHunterActivity::class.java),
        ActionItem("⏱️", "Time Lapse",     "Signal over time",   TimeLapseActivity::class.java),
        ActionItem("🕸️", "Mesh Optimizer","AP placement",        MeshOptimizerActivity::class.java),
        ActionItem("🌡️", "Heatmap",       "Coverage map",       HeatmapActivity::class.java),
        ActionItem("📋", "Network List",   "All networks",       NetworkListActivity::class.java),
        ActionItem("⚙️", "Settings",       "Configure app",      SettingsActivity::class.java),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActionGrid()
        updateSignalInfo()

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> true
                R.id.nav_ar       -> { launch(ArMappingActivity::class.java); true }
                R.id.nav_radar    -> { launch(LiveRadarActivity::class.java); true }
                R.id.nav_analytics-> { launch(AnalyticsDashboardActivity::class.java); true }
                R.id.nav_security -> { launch(SecurityAuditActivity::class.java); true }
                else -> true
            }
        }
    }

    private fun setupActionGrid() {
        binding.actionGrid.layoutManager = GridLayoutManager(this, 2)
        binding.actionGrid.adapter = object : RecyclerView.Adapter<ActionVH>() {
            override fun getItemCount() = actions.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionVH {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_action_card, parent, false)
                return ActionVH(v)
            }
            override fun onBindViewHolder(holder: ActionVH, position: Int) {
                val a = actions[position]
                holder.icon.text     = a.icon
                holder.title.text    = a.title
                holder.subtitle.text = a.subtitle
                holder.card.setOnClickListener { launch(a.targetClass) }
            }
        }
    }

    private fun updateSignalInfo() {
        try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            if (info != null && info.networkId != -1) {
                binding.rssiText.text = "${info.rssi} dBm"
                val ssid = info.ssid?.removeSurrounding("\"") ?: "Unknown"
                binding.ssidText.text = "Connected: $ssid"
            } else {
                binding.rssiText.text = "-- dBm"
                binding.ssidText.text = "Not connected"
            }
        } catch (e: Exception) {
            binding.rssiText.text = "-- dBm"
            binding.ssidText.text = "Permission required"
        }
    }

    private fun launch(cls: Class<*>) = startActivity(Intent(this, cls))

    class ActionVH(v: View) : RecyclerView.ViewHolder(v) {
        val card: MaterialCardView = v.findViewById(R.id.actionCard)
        val icon: TextView         = v.findViewById(R.id.actionIcon)
        val title: TextView        = v.findViewById(R.id.actionTitle)
        val subtitle: TextView     = v.findViewById(R.id.actionSubtitle)
    }
}
