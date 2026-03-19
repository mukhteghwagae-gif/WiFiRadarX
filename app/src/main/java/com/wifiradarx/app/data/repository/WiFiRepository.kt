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
import com.wifiradarx.app.data.dao.DeviceFingerprintDao
import com.wifiradarx.app.data.dao.HourlyBaselineDao
import com.wifiradarx.app.data.dao.SessionMetadataDao
import com.wifiradarx.app.data.dao.SignalSampleDao
import com.wifiradarx.app.data.dao.TrustedBssidDao
import com.wifiradarx.app.data.dao.WifiScanDao
import com.wifiradarx.app.data.entity.DeviceFingerprint
import com.wifiradarx.app.data.entity.HourlyBaseline
import com.wifiradarx.app.data.entity.SessionMetadata
import com.wifiradarx.app.data.entity.TrustedBssidProfile
import com.wifiradarx.app.data.entity.WifiScanResult
import com.wifiradarx.app.intelligence.ChannelAnalyzer
import com.wifiradarx.app.intelligence.OuiLookup
import com.wifiradarx.app.intelligence.SecurityAuditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    // Dedicated coroutine scope so the rescan timer outlives BroadcastReceiver calls
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var rescanJob: Job? = null

    // Android 9+ throttles startScan() to 4 calls per 2 min → 8 s minimum is safe
    private val RESCAN_INTERVAL_MS = 8_000L

    private val ouiLookup = OuiLookup(context)
    private val securityAuditor = SecurityAuditor()

    // ── Session management ────────────────────────────────────────────────────

    fun startSession(): String {
        currentSessionId = UUID.randomUUID().toString()
        return currentSessionId
    }

    fun getCurrentSessionId(): String = currentSessionId

    // ── Continuous scanning ───────────────────────────────────────────────────

    fun startScanning(onResults: (List<ScanResult>) -> Unit = {}) {
        if (_isScanning.value) return
        if (!hasLocationPermission()) {
            _isScanning.value = false
            return
        }
        _isScanning.value = true

        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                try {
                    if (hasLocationPermission()) {
                        @Suppress("DEPRECATION")
                        val results: List<ScanResult> = wifiManager.scanResults ?: emptyList()
                        _scanResults.value = results
                        onResults(results)
                    }
                } catch (e: SecurityException) {
                    // permission revoked while scanning
                }
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

        // Safety-net: some ROMs suppress the broadcast — force a rescan after the interval
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
                } catch (e: Exception) {
                    // startScan() can throw on some devices
                }
            }
        }
    }

    fun triggerScan(): Boolean {
        if (!hasLocationPermission()) return false
        return try {
            scheduleRescan()
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } catch (e: Exception) {
            false
        }
    }

    fun stopScanning() {
        _isScanning.value = false
        rescanJob?.cancel()
        rescanJob = null
        try {
            scanReceiver?.let { context.unregisterReceiver(it) }
        } catch (e: Exception) {
            // receiver was never registered or already unregistered
        }
        scanReceiver = null
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    suspend fun saveScanResults(
        results: List<ScanResult>,
        posX: Float = 0f,
        posY: Float = 0f,
        posZ: Float = 0f
    ) {
        val ts = System.currentTimeMillis()
        val entities: List<WifiScanResult> = results.map { sr ->
            val oui: String = ouiLookup.lookup(sr.BSSID ?: "")
            val channel: Int = ChannelAnalyzer.frequencyToChannel(sr.frequency)
            val secScore: Int = securityAuditor.scoreCapabilities(sr.capabilities ?: "")
            val timestampMicros: Long =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) sr.timestamp
                else 0L
            val centerFreq0: Int =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) sr.centerFreq0 else 0
            val centerFreq1: Int =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) sr.centerFreq1 else 0
            val channelWidth: Int =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) sr.channelWidth else 0

            WifiScanResult(
                sessionId       = currentSessionId,
                ssid            = sr.SSID ?: "",
                bssid           = sr.BSSID ?: "",
                rssi            = sr.level,
                frequency       = sr.frequency,
                channel         = channel,
                capabilities    = sr.capabilities ?: "",
                vendorOui       = oui,
                timestampMicros = timestampMicros,
                timestamp       = ts,
                posX            = posX,
                posY            = posY,
                posZ            = posZ,
                securityScore   = secScore,
                is5GHz          = sr.frequency in 5000..5999,
                is6GHz          = sr.frequency in 6000..6999,
                centerFreq0     = centerFreq0,
                centerFreq1     = centerFreq1,
                channelWidth    = channelWidth
            )
        }
        wifiScanDao.insertAll(entities)

        val existing: SessionMetadata? = sessionMetadataDao.getById(currentSessionId)
        if (existing == null) {
            sessionMetadataDao.insert(
                SessionMetadata(
                    sessionId = currentSessionId,
                    startTime = ts,
                    scanCount = 1,
                    apCount   = results.size
                )
            )
        } else {
            sessionMetadataDao.update(
                existing.copy(
                    scanCount = existing.scanCount + 1,
                    apCount   = maxOf(existing.apCount, results.size),
                    endTime   = ts
                )
            )
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    fun getAllScansFlow() = wifiScanDao.getAllFlow()
    fun getSessionScansFlow(sessionId: String) = wifiScanDao.getBySessionFlow(sessionId)
    fun getRogueSuspectsFlow() = wifiScanDao.getRogueSuspectsFlow()
    fun getAllSessionsFlow() = sessionMetadataDao.getAllFlow()
    fun getAllFingerprintsFlow() = deviceFingerprintDao.getAllFlow()
    fun getAllTrustedProfilesFlow() = trustedBssidDao.getAllFlow()

    suspend fun getLatestSession(): SessionMetadata? = sessionMetadataDao.getLatest()

    suspend fun saveFingerprint(fp: DeviceFingerprint) = deviceFingerprintDao.insert(fp)
    suspend fun getFingerprint(bssid: String): DeviceFingerprint? =
        deviceFingerprintDao.getByBssid(bssid)

    suspend fun addTrustedProfile(profile: TrustedBssidProfile) =
        trustedBssidDao.insert(profile)
    suspend fun getTrustedProfile(bssid: String): TrustedBssidProfile? =
        trustedBssidDao.getByBssid(bssid)

    suspend fun updateBaseline(bssid: String, hourOfWeek: Int, rssi: Float) {
        val existing: HourlyBaseline? = hourlyBaselineDao.getBaseline(bssid, hourOfWeek)
        val alpha = 0.15f
        if (existing == null) {
            // Use named arguments to skip the auto-generated id:Long field
            hourlyBaselineDao.insert(
                HourlyBaseline(
                    bssid       = bssid,
                    hourOfWeek  = hourOfWeek,
                    emaRssi     = rssi,
                    emaVariance = 0f,
                    sampleCount = 1
                )
            )
        } else {
            val newEma: Float = alpha * rssi + (1f - alpha) * existing.emaRssi
            val diff: Float = rssi - existing.emaRssi
            val newVar: Float = alpha * diff * diff + (1f - alpha) * existing.emaVariance
            hourlyBaselineDao.update(
                existing.copy(
                    emaRssi     = newEma,
                    emaVariance = newVar,
                    sampleCount = existing.sampleCount + 1
                )
            )
        }
    }

    suspend fun getBaseline(bssid: String, hourOfWeek: Int): HourlyBaseline? =
        hourlyBaselineDao.getBaseline(bssid, hourOfWeek)

    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}
