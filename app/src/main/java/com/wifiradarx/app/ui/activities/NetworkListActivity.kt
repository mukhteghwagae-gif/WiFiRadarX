package com.wifiradarx.app.ui.activities

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wifiradarx.app.R
import com.wifiradarx.app.databinding.ActivityNetworkListBinding
import com.wifiradarx.app.intelligence.SecurityAuditor

class NetworkListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNetworkListBinding

    data class NetworkEntry(
        val ssid: String,
        val bssid: String,
        val rssi: Int,
        val frequency: Int,
        val capabilities: String,
        val securityScore: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNetworkListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Network List"
        }

        val auditor = SecurityAuditor()
        val networks = listOf(
            NetworkEntry("HomeNetwork_5G", "AA:BB:CC:11:22:33", -52, 5180, "[WPA3-SAE]", 0),
            NetworkEntry("OfficeWiFi", "DD:EE:FF:44:55:66", -65, 2437, "[WPA2-PSK]", 0),
            NetworkEntry("FREE_Public", "11:22:33:AA:BB:CC", -70, 2412, "[ESS]", 0),
            NetworkEntry("HomeNetwork_2G", "AA:BB:CC:11:22:34", -68, 2412, "[WPA2-PSK][WPS]", 0),
            NetworkEntry("Neighbor_AP", "55:66:77:88:99:00", -85, 5200, "[WPA2-PSK]", 0)
        ).map { it.copy(securityScore = auditor.audit(it.capabilities)) }
            .sortedByDescending { it.rssi }

        binding.networkRecycler.apply {
            layoutManager = LinearLayoutManager(this@NetworkListActivity)
            adapter = NetworkAdapter(networks)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
        binding.networkCountText.text = "${networks.size} networks found"
    }

    inner class NetworkAdapter(private val items: List<NetworkEntry>) :
        RecyclerView.Adapter<NetworkAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ssid: TextView = v.findViewById(R.id.netSsid)
            val bssid: TextView = v.findViewById(R.id.netBssid)
            val rssi: TextView = v.findViewById(R.id.netRssi)
            val security: TextView = v.findViewById(R.id.netSecurity)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(layoutInflater.inflate(R.layout.item_network, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val n = items[position]
            val band = if (n.frequency > 4000) "5GHz" else "2.4GHz"
            val ch = if (n.frequency > 4000) (n.frequency - 5000) / 5 else (n.frequency - 2407) / 5
            holder.ssid.text = n.ssid
            holder.bssid.text = "${n.bssid}  •  $band  •  Ch$ch"
            holder.rssi.text = "${n.rssi} dBm"
            holder.rssi.setTextColor(
                when {
                    n.rssi > -60 -> Color.parseColor("#4BFF4B")
                    n.rssi > -75 -> Color.parseColor("#FFB84B")
                    else -> Color.parseColor("#FF4B4B")
                }
            )
            val secLabel = when {
                n.capabilities.contains("WPA3") -> "WPA3"
                n.capabilities.contains("WPA2") -> "WPA2"
                n.capabilities.contains("WEP") -> "WEP ⚠"
                else -> "OPEN ⚠"
            }
            holder.security.text = secLabel
            holder.security.setTextColor(
                if (n.securityScore >= 50) Color.parseColor("#4BFF4B")
                else Color.parseColor("#FF4B4B")
            )
        }

        override fun getItemCount() = items.size
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
