package com.wifiradarx.app.ui.adapters

import android.graphics.Color
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wifiradarx.app.R
import com.wifiradarx.app.data.entity.WifiScanResult
import com.wifiradarx.app.intelligence.SecurityAuditor

class NetworkAdapter(
    private val onClick: (WifiScanResult) -> Unit = {}
) : ListAdapter<WifiScanResult, NetworkAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WifiScanResult>() {
            override fun areItemsTheSame(a: WifiScanResult, b: WifiScanResult) = a.id == b.id
            override fun areContentsTheSame(a: WifiScanResult, b: WifiScanResult) = a == b
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvSsid: TextView = view.findViewById(R.id.tv_ssid)
        val tvBssid: TextView = view.findViewById(R.id.tv_bssid)
        val tvRssi: TextView = view.findViewById(R.id.tv_rssi)
        val tvChannel: TextView = view.findViewById(R.id.tv_channel)
        val tvVendor: TextView = view.findViewById(R.id.tv_vendor)
        val tvSecurity: TextView = view.findViewById(R.id.tv_security)
        val tvBand: TextView = view.findViewById(R.id.tv_band)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_network, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvSsid.text = item.ssid.ifBlank { "<hidden>" }
        holder.tvBssid.text = item.bssid
        holder.tvRssi.text = "${item.rssi} dBm"
        holder.tvChannel.text = "Ch ${item.channel}"
        holder.tvVendor.text = item.vendorOui.ifBlank { "Unknown" }

        val auditor = SecurityAuditor()
        val result = auditor.audit(item.ssid, item.capabilities, item.rssi)
        holder.tvSecurity.text = result.level.name
        holder.tvSecurity.setTextColor(
            when (result.level) {
                SecurityAuditor.SecurityLevel.SECURE -> Color.parseColor("#00FF88")
                SecurityAuditor.SecurityLevel.CAUTION -> Color.parseColor("#FFCC00")
                SecurityAuditor.SecurityLevel.DANGER -> Color.parseColor("#FF4444")
            }
        )
        holder.tvBand.text = when {
            item.is6GHz -> "6 GHz"
            item.is5GHz -> "5 GHz"
            else -> "2.4 GHz"
        }
        holder.itemView.setOnClickListener { onClick(item) }

        // Highlight rogue suspects
        holder.itemView.setBackgroundColor(
            if (item.isRogueSuspect) Color.parseColor("#22FF0000") else Color.TRANSPARENT
        )
    }
}

class SessionAdapter(
    private val onClick: (com.wifiradarx.app.data.entity.SessionMetadata) -> Unit
) : ListAdapter<com.wifiradarx.app.data.entity.SessionMetadata,
        SessionAdapter.VH>(SESSION_DIFF) {

    companion object {
        val SESSION_DIFF = object : DiffUtil.ItemCallback<com.wifiradarx.app.data.entity.SessionMetadata>() {
            override fun areItemsTheSame(
                a: com.wifiradarx.app.data.entity.SessionMetadata,
                b: com.wifiradarx.app.data.entity.SessionMetadata
            ) = a.sessionId == b.sessionId
            override fun areContentsTheSame(
                a: com.wifiradarx.app.data.entity.SessionMetadata,
                b: com.wifiradarx.app.data.entity.SessionMetadata
            ) = a == b
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvId: TextView = view.findViewById(R.id.tv_session_id)
        val tvTime: TextView = view.findViewById(R.id.tv_session_time)
        val tvStats: TextView = view.findViewById(R.id.tv_session_stats)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = getItem(position)
        holder.tvId.text = "Session ${s.sessionId.takeLast(8)}"
        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
        holder.tvTime.text = fmt.format(java.util.Date(s.startTime))
        holder.tvStats.text = "${s.scanCount} scans · ${s.apCount} APs"
        holder.itemView.setOnClickListener { onClick(s) }
    }
}
