package com.wifiradarx.app.ui.activities

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.widget.TextView
import com.wifiradarx.app.R
import com.wifiradarx.app.databinding.ActivityDeviceHunterBinding

class DeviceHunterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDeviceHunterBinding

    data class DeviceEntry(
        val bssid: String,
        val ssid: String,
        val vendor: String,
        val rssi: Int,
        val threatScore: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceHunterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Device Hunter"
        }

        val devices = listOf(
            DeviceEntry("AA:BB:CC:11:22:33", "HomeNet_5G", "TP-Link", -55, 5),
            DeviceEntry("DD:EE:FF:44:55:66", "Unknown_AP", "Unknown", -72, 65),
            DeviceEntry("11:22:33:44:55:66", "GuestWiFi", "Netgear", -68, 10),
            DeviceEntry("AA:11:22:33:44:55", "FREE_WiFi", "Unknown", -60, 80),
            DeviceEntry("CC:DD:EE:FF:00:11", "Office_5GHz", "Cisco", -50, 8)
        )

        binding.deviceList.layoutManager = LinearLayoutManager(this)
        binding.deviceList.adapter = DeviceAdapter(devices)
        binding.deviceCountText.text = "${devices.size} devices detected"
    }

    inner class DeviceAdapter(private val items: List<DeviceEntry>) :
        RecyclerView.Adapter<DeviceAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ssid: TextView = view.findViewById(R.id.itemSsid)
            val bssid: TextView = view.findViewById(R.id.itemBssid)
            val vendor: TextView = view.findViewById(R.id.itemVendor)
            val threat: TextView = view.findViewById(R.id.itemThreat)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = layoutInflater.inflate(R.layout.item_device, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val d = items[position]
            holder.ssid.text = d.ssid
            holder.bssid.text = "${d.bssid}  •  ${d.rssi} dBm"
            holder.vendor.text = d.vendor
            holder.threat.text = "Threat: ${d.threatScore}%"
            holder.threat.setTextColor(
                when {
                    d.threatScore >= 60 -> Color.parseColor("#FF4B4B")
                    d.threatScore >= 30 -> Color.parseColor("#FFB84B")
                    else -> Color.parseColor("#4BFF4B")
                }
            )
        }

        override fun getItemCount() = items.size
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
