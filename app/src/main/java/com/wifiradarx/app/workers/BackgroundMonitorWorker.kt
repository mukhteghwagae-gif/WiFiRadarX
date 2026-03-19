package com.wifiradarx.app.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.wifiradarx.app.WiFiRadarXApp
import com.wifiradarx.app.R
import android.net.wifi.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class BackgroundMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as WiFiRadarXApp
        val repo = app.wifiRepository

        if (!repo.isWifiEnabled()) return@withContext Result.success()

        // Trigger a scan and wait briefly for results
        repo.triggerScan()
        kotlinx.coroutines.delay(3000L)

        val results = repo.scanResultsFlow.value
        if (results.isEmpty()) return@withContext Result.success()

        repo.saveScanResults(results)

        // Check for rogue APs
        val rogueDetector = app.intelligenceEngine.rogueApDetector
        val threats = results.filter { sr ->
            rogueDetector.assess(
                sr.BSSID ?: "",
                sr.SSID ?: "",
                channel = 0,
                capabilities = sr.capabilities ?: "",
                vendorOui = "",
                rssi = sr.level
            ).isAlert
        }

        if (threats.isNotEmpty()) {
            sendThreatNotification(threats.size)
        }

        Result.success()
    }

    private fun sendThreatNotification(count: Int) {
        val notification = NotificationCompat.Builder(applicationContext, WiFiRadarXApp.CHANNEL_THREAT)
            .setSmallIcon(R.drawable.ic_wifi_alert)
            .setContentTitle("⚠ Rogue AP Detected")
            .setContentText("$count suspicious network(s) found near you.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(
                applicationContext, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_THREAT_ID, notification)
        }
    }

    companion object {
        const val WORK_NAME = "wfx_background_monitor"
        const val NOTIF_THREAT_ID = 1001

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<BackgroundMonitorWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            // Re-schedule background work after reboot if setting is enabled
            BackgroundMonitorWorker.schedule(context)
        }
    }
}
