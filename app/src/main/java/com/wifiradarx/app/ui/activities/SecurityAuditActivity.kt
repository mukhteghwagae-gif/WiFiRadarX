package com.wifiradarx.app.ui.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.wifiradarx.app.databinding.ActivitySecurityAuditBinding
import com.wifiradarx.app.intelligence.SecurityAuditor

class SecurityAuditActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySecurityAuditBinding
    private val auditor = SecurityAuditor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityAuditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "Security Audit"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        runAudit()
    }

    private fun runAudit() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            binding.securityScoreText.text = "–"
            binding.securityBadge.text = "NEEDS LOCATION PERMISSION"
            return
        }
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val results = wifiManager.scanResults ?: emptyList()

        if (results.isEmpty()) {
            binding.securityScoreText.text = "N/A"
            binding.securityBadge.text = "NO SCAN DATA"
            return
        }

        // Audit each network, show average score for connected network
        val connected = wifiManager.connectionInfo
        val primary = results.firstOrNull { it.BSSID == connected.bssid }
        val score = if (primary != null) auditor.audit(primary.capabilities) else
            results.map { auditor.audit(it.capabilities) }.average().toInt()

        binding.securityScoreText.text = score.toString()
        when {
            score >= 70 -> {
                binding.securityBadge.text = "SECURE"
                binding.securityBadge.setTextColor(Color.parseColor("#4BFF4B"))
            }
            score >= 40 -> {
                binding.securityBadge.text = "MODERATE RISK"
                binding.securityBadge.setTextColor(Color.parseColor("#FFB84B"))
            }
            else -> {
                binding.securityBadge.text = "HIGH RISK"
                binding.securityBadge.setTextColor(Color.parseColor("#FF4B4B"))
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
