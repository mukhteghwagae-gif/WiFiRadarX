package com.wifiradarx.app.ui.activities

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wifiradarx.app.R
import com.wifiradarx.app.data.entity.WifiScanResult
import com.wifiradarx.app.intelligence.SecurityAuditor
import com.wifiradarx.app.ui.viewmodel.MainViewModel
import com.wifiradarx.app.ui.viewmodel.SecurityViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SecurityAuditActivity : AppCompatActivity() {

    private val vm     : SecurityViewModel by viewModels()
    private val mainVm : MainViewModel     by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_audit)
        supportActionBar?.title = "Security Audit"

        val rv = findViewById<RecyclerView>(R.id.rv_security)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = AuditAdapter()
        rv.adapter = adapter

        lifecycleScope.launch {
            mainVm.allScans.collectLatest { scans -> vm.audit(scans) }
        }
        lifecycleScope.launch {
            vm.auditResults.collect { results -> adapter.submitList(results) }
        }
        lifecycleScope.launch {
            vm.overallRating.collect { rating ->
                findViewById<TextView>(R.id.tv_overall_rating)?.text = rating
            }
        }
    }
}

class AuditAdapter :
    RecyclerView.Adapter<AuditAdapter.VH>() {

    private var items: List<Pair<WifiScanResult, SecurityAuditor.AuditResult>> = emptyList()

    fun submitList(list: List<Pair<WifiScanResult, SecurityAuditor.AuditResult>>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvSsid   : TextView = v.findViewById(R.id.tv_audit_ssid)
        val tvScore  : TextView = v.findViewById(R.id.tv_audit_score)
        val tvBadges : TextView = v.findViewById(R.id.tv_audit_badges)
        val tvIssues : TextView = v.findViewById(R.id.tv_audit_issues)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_audit, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (scan, audit) = items[position]
        holder.tvSsid.text   = scan.ssid.ifBlank { "<hidden>" }
        holder.tvScore.text  = "${audit.score}/100"
        holder.tvScore.setTextColor(
            when (audit.level) {
                SecurityAuditor.SecurityLevel.SECURE  -> Color.parseColor("#00FF88")
                SecurityAuditor.SecurityLevel.CAUTION -> Color.parseColor("#FFCC00")
                SecurityAuditor.SecurityLevel.DANGER  -> Color.parseColor("#FF4444")
            }
        )
        holder.tvBadges.text = audit.badges.joinToString(" · ")
        holder.tvIssues.text = audit.issues.joinToString(" • ").ifBlank { "No issues" }
    }
}
