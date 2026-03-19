package com.wifiradarx.app.data.repository

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.wifiradarx.app.data.dao.*
import com.wifiradarx.app.data.entity.*
import com.wifiradarx.app.intelligence.ChannelAnalyzer
import com.wifiradarx.app.intelligence.OuiLookup
import com.wifiradarx.app.intelligence.SecurityAuditor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class WiFiRepository(
    private val context: Context,
    private val wifiScanDao: WifiScanDao,
    private val deviceFingerprintDao: DeviceFingerprintDao,
    private val trustedBssidDao: TrustedBssidDao,
    private val hourlyBaselineDao: HourlyBaselineDao,
    private val signalSampleDao: SignalSampleDao,
    private val sessionMetadataDao: SessionMetadataDao
) {
    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResultsFlow: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var currentSessionId: String = UUID.randomUUID().toString()
    private var scanReceiver: BroadcastReceiver? = null

    // ── FIX 1: Continuous-scanning infrastructure ─────────────────────────
    // A dedicated scope outliving individual BroadcastReceiver calls.
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var rescanJob: Job? = null

    // Android 9+ throttles startScan() to 4 per 2 min in foreground → 8 s is safe.
    private val RESCAN_INTERVAL_MS = 8_000L

    private val ouiLookup = OuiLookup(context)
    private val securityAuditor = SecurityAuditor()

    fun startSession(): String {
        currentSessionId = UUID.randomUUID().toString()
        return currentSessionId
    }

    fun getCurrentSessionId(): String = currentSessionId

    fun startScanning(onResults: (List<ScanResult>) -> Unit = {}) {
        if (_isScanning.value) return
        if (!hasLocationPermission()) { _isScanning.value = false; return }

        _isScanning.value = true

        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                try {
                    if (hasLocationPermission()) {
                        @Suppress("DEPRECATION")
                        val results = wifiManager.scanResults ?: emptyList()
                        _scanResults.value = results
                        onResults(results)
                    }
                } catch (_: SecurityException) {}

                // ── Schedule the NEXT scan automatically ──────────────────
                scheduleRescan()
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(scanReceiver, filter)
        }

        @Suppress("DEPRECATION")
        wifiManager.startScan()

        // Safety net: if the broadcast never fires (some ROMs suppress it),
        // kick off the next attempt after the interval.
        scheduleRescan()
    }

    private fun scheduleRescan() {
        rescanJob?.cancel()
        if (!_isScanning.value) return
        rescanJob = repoScope.launch {
            delay(RESCAN_INTERVAL_MS)
            if (_isScanning.value) {
                try {
                    @Suppress("DEPRECATION")
                    wifiManager.startScan()
                } catch (_: Exception) {}
            }
        }
    }

    fun triggerScan(): Boolean {
        if (!hasLocationPermission()) return false
        return try {
            scheduleRescan()
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } catch (_: Exception) { false }
    }

    fun stopScanning() {
        _isScanning.value = false
        rescanJob?.cancel()
        rescanJob = null
        scanReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        scanReceiver = null
    }

    suspend fun saveScanResults(
        results: List<ScanResult>,
        posX: Float = 0f,
        posY: Float = 0f,
        posZ: Float = 0f
    ) {
        val entities = results.map { sr ->
            val oui = ouiLookup.lookup(sr.BSSID)
            val channel = ChannelAnalyzer.frequencyToChannel(sr.frequency)
            val secScore = securityAuditor.scoreCapabilities(sr.capabilities ?: "")
            val ts = System.currentTimeMillis()
            WifiScanResult(
                sessionId = currentSessionId,
                ssid = sr.SSID ?: "",
                bssid = sr.BSSID ?: "",
                rssi = sr.level,
                frequency = sr.frequency,
                channel = channel,
                capabilities = sr.capabilities ?: "",
                vendorOui = oui,
                timestampMicros = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                    sr.timestamp else 0L,
                timestamp = ts,
                posX = posX,
                posY = posY,
                posZ = posZ,
                securityScore = secScore,
                is5GHz = sr.frequency in 5000..5999,
                is6GHz = sr.frequency in 6000..6999,
                centerFreq0 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) sr.centerFreq0 else 0,
                centerFreq1 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) sr.centerFreq1 else 0,
                channelWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) sr.channelWidth else 0
            )
        }
        wifiScanDao.insertAll(entities)

        val existing = sessionMetadataDao.getById(currentSessionId)
        if (existing == null) {
            sessionMetadataDao.insert(
                SessionMetadata(
                    sessionId = currentSessionId,
                    startTime = System.currentTimeMillis(),
                    scanCount = 1,
                    apCount = results.size
                )
            )
        } else {
            sessionMetadataDao.update(
                existing.copy(
                    scanCount = existing.scanCount + 1,
                    apCount = maxOf(existing.apCount, results.size),
                    endTime = System.currentTimeMillis()
                )
            )
        }
    }

    fun getAllScansFlow() = wifiScanDao.getAllFlow()
    fun getSessionScansFlow(sessionId: String) = wifiScanDao.getBySessionFlow(sessionId)
    fun getRogueSuspectsFlow() = wifiScanDao.getRogueSuspectsFlow()
    fun getAllSessionsFlow() = sessionMetadataDao.getAllFlow()
    fun getAllFingerprintsFlow() = deviceFingerprintDao.getAllFlow()
    fun getAllTrustedProfilesFlow() = trustedBssidDao.getAllFlow()

    suspend fun getLatestSession() = sessionMetadataDao.getLatest()
    suspend fun saveFingerprint(fp: DeviceFingerprint) = deviceFingerprintDao.insert(fp)
    suspend fun getFingerprint(bssid: String) = deviceFingerprintDao.getByBssid(bssid)
    suspend fun addTrustedProfile(profile: TrustedBssidProfile) = trustedBssidDao.insert(profile)
    suspend fun getTrustedProfile(bssid: String) = trustedBssidDao.getByBssid(bssid)

    suspend fun updateBaseline(bssid: String, hourOfWeek: Int, rssi: Float) {
        val existing = hourlyBaselineDao.getBaseline(bssid, hourOfWeek)
        val alpha = 0.15f
        if (existing == null) {
            hourlyBaselineDao.insert(HourlyBaseline(bssid, hourOfWeek, rssi, 0f, 1))
        } else {
            val newEma = alpha * rssi + (1 - alpha) * existing.emaRssi
            val diff = rssi - existing.emaRssi
            val newVar = alpha * diff * diff + (1 - alpha) * existing.emaVariance
            hourlyBaselineDao.update(existing.copy(emaRssi = newEma, emaVariance = newVar,
                sampleCount = existing.sampleCount + 1))
        }
    }

    suspend fun getBaseline(bssid: String, hourOfWeek: Int) =
        hourlyBaselineDao.getBaseline(bssid, hourOfWeek)

    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}
