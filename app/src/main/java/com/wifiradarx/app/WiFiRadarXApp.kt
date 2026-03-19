package com.wifiradarx.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.wifiradarx.app.data.database.WiFiRadarDatabase
import com.wifiradarx.app.data.repository.WiFiRepository
import com.wifiradarx.app.export.ExportManager
import com.wifiradarx.app.intelligence.OuiLookup
import com.wifiradarx.app.intelligence.SignalIntelligenceEngine

class WiFiRadarXApp : Application() {

    val database by lazy { WiFiRadarDatabase.getInstance(this) }

    val wifiRepository by lazy {
        WiFiRepository(
            context = this,
            wifiScanDao = database.wifiScanDao(),
            deviceFingerprintDao = database.deviceFingerprintDao(),
            trustedBssidDao = database.trustedBssidDao(),
            hourlyBaselineDao = database.hourlyBaselineDao(),
            signalSampleDao = database.signalSampleDao(),
            sessionMetadataDao = database.sessionMetadataDao()
        )
    }

    val intelligenceEngine by lazy { SignalIntelligenceEngine() }
    val exportManager by lazy { ExportManager(this) }
    val ouiLookup by lazy { OuiLookup(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SCAN, "Background Scanning",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Ongoing WiFi scan notification" }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_THREAT, "Threat Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Rogue AP and security threat alerts" }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ANOMALY, "Signal Anomalies",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Unusual signal pattern alerts" }
            )
        }
    }

    companion object {
        const val CHANNEL_SCAN = "wfx_scan"
        const val CHANNEL_THREAT = "wfx_threat"
        const val CHANNEL_ANOMALY = "wfx_anomaly"
    }
}
