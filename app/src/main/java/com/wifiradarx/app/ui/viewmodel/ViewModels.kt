package com.wifiradarx.app.ui.viewmodel

import android.app.Application
import android.net.wifi.ScanResult
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wifiradarx.app.WiFiRadarXApp
import com.wifiradarx.app.data.entity.WifiScanResult
import com.wifiradarx.app.intelligence.*
import com.wifiradarx.app.utils.AppSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.*

// ─── Analytics ────────────────────────────────────────────────────────────────
class AnalyticsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as WiFiRadarXApp).wifiRepository

    val allScans = repo.getAllScansFlow()
    val sessions = repo.getAllSessionsFlow()

    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId

    fun selectSession(id: String?) { _selectedSessionId.value = id }
    fun getSessionScans(sessionId: String) = repo.getSessionScansFlow(sessionId)
}

// ─── Channel Analyzer ─────────────────────────────────────────────────────────
class ChannelViewModel(app: Application) : AndroidViewModel(app) {
    private val wfxApp = app as WiFiRadarXApp
    private val engine = wfxApp.intelligenceEngine

    private val _channelStats =
        MutableStateFlow<Map<Int, ChannelAnalyzer.ChannelStats>>(emptyMap())
    val channelStats: StateFlow<Map<Int, ChannelAnalyzer.ChannelStats>> = _channelStats

    private val _best24 =
        MutableStateFlow<List<ChannelAnalyzer.ChannelRecommendation>>(emptyList())
    val best24: StateFlow<List<ChannelAnalyzer.ChannelRecommendation>> = _best24

    private val _best5 =
        MutableStateFlow<List<ChannelAnalyzer.ChannelRecommendation>>(emptyList())
    val best5: StateFlow<List<ChannelAnalyzer.ChannelRecommendation>> = _best5

    private val _optimizedChannels =
        MutableStateFlow<List<SimulatedAnnealingOptimizer.ApNode>>(emptyList())
    val optimizedChannels: StateFlow<List<SimulatedAnnealingOptimizer.ApNode>> = _optimizedChannels

    fun analyze(scans: List<WifiScanResult>) {
        viewModelScope.launch {
            val pairs = scans.map { it.frequency to it.rssi }
            val stats = ChannelAnalyzer.analyzeChannels(pairs)
            _channelStats.value = stats
            _best24.value = ChannelAnalyzer.getBestChannels(stats, ChannelAnalyzer.Band.GHZ_2_4)
            _best5.value  = ChannelAnalyzer.getBestChannels(stats, ChannelAnalyzer.Band.GHZ_5)

            // FIX: give each AP a unique virtual position so SA optimizer works meaningfully
            val apNodes = scans.distinctBy { it.bssid }.mapIndexed { i, s ->
                val (vx, vz) = virtualPosition(s.bssid, i, scans.distinctBy { it.bssid }.size)
                SimulatedAnnealingOptimizer.ApNode(
                    id   = s.bssid,
                    x    = vx,
                    y    = vz,
                    band = ChannelAnalyzer.getBand(s.frequency)
                )
            }
            if (apNodes.isNotEmpty()) {
                _optimizedChannels.value = engine.saOptimizer.optimize(apNodes)
            }
        }
    }
}

// ─── Security Audit ───────────────────────────────────────────────────────────
class SecurityViewModel(app: Application) : AndroidViewModel(app) {
    private val repo    = (app as WiFiRadarXApp).wifiRepository
    private val auditor = SecurityAuditor()

    private val _auditResults =
        MutableStateFlow<List<Pair<WifiScanResult, SecurityAuditor.AuditResult>>>(emptyList())
    val auditResults: StateFlow<List<Pair<WifiScanResult, SecurityAuditor.AuditResult>>> =
        _auditResults

    private val _overallRating = MutableStateFlow("Waiting for scan…")
    val overallRating: StateFlow<String> = _overallRating

    val rogueAlerts = repo.getRogueSuspectsFlow()

    /** Audit from persisted DB records. */
    fun audit(scans: List<WifiScanResult>) {
        viewModelScope.launch {
            val results = scans.distinctBy { it.bssid }
                .map { s -> s to auditor.audit(s.ssid, s.capabilities, s.rssi) }
                .sortedBy { it.second.score }
            _auditResults.value = results
            _overallRating.value = auditor.getOverallRating(results.map { it.second.score })
        }
    }

    /**
     * FIX: Audit from live ScanResult list when DB is still empty.
     * Skips if DB results are already populated to avoid duplicating entries.
     */
    fun auditLive(liveResults: List<ScanResult>) {
        if (liveResults.isEmpty() || _auditResults.value.isNotEmpty()) return
        viewModelScope.launch {
            val stubs = liveResults.map { sr ->
                WifiScanResult(
                    sessionId       = "live",
                    ssid            = sr.SSID ?: "",
                    bssid           = sr.BSSID ?: "",
                    rssi            = sr.level,
                    frequency       = sr.frequency,
                    channel         = ChannelAnalyzer.frequencyToChannel(sr.frequency),
                    capabilities    = sr.capabilities ?: "",
                    vendorOui       = "",
                    timestampMicros = 0L,
                    timestamp       = System.currentTimeMillis(),
                    securityScore   = 0,
                    is5GHz          = sr.frequency in 5000..5999,
                    is6GHz          = sr.frequency in 6000..6999
                )
            }.distinctBy { it.bssid }
            val results = stubs
                .map { s -> s to auditor.audit(s.ssid, s.capabilities, s.rssi) }
                .sortedBy { it.second.score }
            _auditResults.value = results
            _overallRating.value = auditor.getOverallRating(results.map { it.second.score })
        }
    }
}

// ─── Network List ─────────────────────────────────────────────────────────────
class NetworkListViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as WiFiRadarXApp).wifiRepository

    val scanResults: StateFlow<List<ScanResult>> = repo.scanResultsFlow
    val allDbScans  = repo.getAllScansFlow()

    private val _filterBand = MutableStateFlow("ALL")
    val filterBand: StateFlow<String> = _filterBand
    fun setFilter(band: String) { _filterBand.value = band }

    /**
     * FIX: Merged flow — live results converted to WifiScanResult stubs when DB is empty.
     * Once DB has data it takes over (richer fields: OUI, security score, etc.).
     */
    val combinedScans: Flow<List<WifiScanResult>> = combine(scanResults, allDbScans) { live, db ->
        if (db.isNotEmpty()) db
        else live.map { sr -> liveToEntity(sr) }
    }

    private fun liveToEntity(sr: ScanResult) = WifiScanResult(
        sessionId       = "live",
        ssid            = sr.SSID ?: "",
        bssid           = sr.BSSID ?: "",
        rssi            = sr.level,
        frequency       = sr.frequency,
        channel         = ChannelAnalyzer.frequencyToChannel(sr.frequency),
        capabilities    = sr.capabilities ?: "",
        vendorOui       = "",
        timestampMicros = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            sr.timestamp else 0L,
        timestamp       = System.currentTimeMillis(),
        securityScore   = SecurityAuditor().scoreCapabilities(sr.capabilities ?: ""),
        is5GHz          = sr.frequency in 5000..5999,
        is6GHz          = sr.frequency in 6000..6999,
        centerFreq0     = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) sr.centerFreq0 else 0,
        centerFreq1     = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) sr.centerFreq1 else 0,
        channelWidth    = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) sr.channelWidth else 0
    )
}

// ─── Mesh Optimizer ───────────────────────────────────────────────────────────
class MeshViewModel(app: Application) : AndroidViewModel(app) {
    private val wfxApp = app as WiFiRadarXApp
    private val engine = wfxApp.intelligenceEngine

    private val _deadZones =
        MutableStateFlow<List<DeadZoneDetector.DeadZone>>(emptyList())
    val deadZones: StateFlow<List<DeadZoneDetector.DeadZone>> = _deadZones

    private val _apRecs =
        MutableStateFlow<List<DeadZoneDetector.ApRecommendation>>(emptyList())
    val apRecs: StateFlow<List<DeadZoneDetector.ApRecommendation>> = _apRecs

    fun compute(scans: List<WifiScanResult>) {
        viewModelScope.launch {
            if (scans.isEmpty()) return@launch
            engine.idwInterpolator.clearSamples()

            // FIX: when all positions are 0,0,0 (no AR walk), use virtual spread layout
            val uniqueAps = scans.distinctBy { it.bssid }
            val allSamePos = uniqueAps.all { it.posX == 0f && it.posZ == 0f }

            uniqueAps.forEachIndexed { i, s ->
                val (vx, vz) = if (allSamePos) virtualPosition(s.bssid, i, uniqueAps.size)
                               else s.posX to s.posZ
                engine.idwInterpolator.addSample(vx, vz, s.rssi.toFloat())
            }

            val bounds = engine.idwInterpolator.getBounds() ?: return@launch
            val grid   = engine.idwInterpolator.buildGrid(
                bounds[0], bounds[1], bounds[2], bounds[3], 24
            )
            val zones  = engine.deadZoneDetector.findDeadZones(
                grid, bounds[0], bounds[1], bounds[2], bounds[3]
            )
            _deadZones.value = zones
            _apRecs.value    = engine.deadZoneDetector.recommendApPlacements(zones)
        }
    }
}

// ─── Heatmap ──────────────────────────────────────────────────────────────────
class HeatmapViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as WiFiRadarXApp).wifiRepository

    val allScans = repo.getAllScansFlow()
    val sessions = repo.getAllSessionsFlow()

    private val _selectedSession = MutableStateFlow<String?>(null)
    val selectedSession: StateFlow<String?> = _selectedSession
    fun selectSession(id: String?) { _selectedSession.value = id }
}

// ─── Settings ─────────────────────────────────────────────────────────────────
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx = app.applicationContext

    val settings = AppSettings.getSettingsFlow(appCtx)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings.Settings())

    fun save(s: AppSettings.Settings) {
        viewModelScope.launch { AppSettings.saveAll(appCtx, s) }
    }
}

// ─── AR Mapping ───────────────────────────────────────────────────────────────
class ArViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as WiFiRadarXApp).wifiRepository

    val scanResults = repo.scanResultsFlow
    val rogues      = repo.getRogueSuspectsFlow()

    private val _predictMode = MutableStateFlow(false)
    val predictMode: StateFlow<Boolean> = _predictMode
    fun togglePredictMode() { _predictMode.value = !_predictMode.value }

    fun saveScan(posX: Float, posY: Float, posZ: Float) {
        viewModelScope.launch {
            repo.saveScanResults(repo.scanResultsFlow.value, posX, posY, posZ)
        }
    }
}

// ─── Device Hunter ────────────────────────────────────────────────────────────
class DeviceHunterViewModel(app: Application) : AndroidViewModel(app) {
    private val wfxApp       = app as WiFiRadarXApp
    private val triangulator = wfxApp.intelligenceEngine.interferenceTriangulator

    // FIX: expose live scan results so activity reads real RSSI
    val liveScanResults = wfxApp.wifiRepository.scanResultsFlow

    private val _source = MutableStateFlow<InterferenceTriangulator.Source?>(null)
    val source: StateFlow<InterferenceTriangulator.Source?> = _source

    private val _measurementCount = MutableStateFlow(0)
    val measurementCount: StateFlow<Int> = _measurementCount

    /**
     * FIX: accepts actual (x,z) tapped by user + real maxRssi at that position.
     * Maps RSSI (-100..-30 dBm) → interference score (0-100).
     */
    fun addMeasurement(x: Float, z: Float, maxRssi: Int) {
        val score = ((maxRssi + 100f) / 70f * 100f).coerceIn(0f, 100f)
        triangulator.addMeasurement(x, z, score)
        _measurementCount.value = triangulator.getMeasurementCount()
        if (triangulator.getMeasurementCount() >= 3) {
            viewModelScope.launch { _source.value = triangulator.triangulate() }
        }
    }

    fun reset() {
        triangulator.clearMeasurements()
        _measurementCount.value = 0
        _source.value = null
    }
}

// ─── TimeLapse ────────────────────────────────────────────────────────────────
class TimeLapseViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as WiFiRadarXApp).wifiRepository

    val sessions = repo.getAllSessionsFlow()

    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId

    private val _playbackPosition = MutableStateFlow(0f)
    val playbackPosition: StateFlow<Float> = _playbackPosition

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _diffMode = MutableStateFlow(false)
    val diffMode: StateFlow<Boolean> = _diffMode

    /**
     * FIX: sessionScans is built by flatMap-ing the selected session's DB flow,
     * then slicing it by playback position timestamp.
     *
     * We use flatMapLatest so switching sessions cancels the previous DB subscription.
     * The position slice is applied inside a map so it re-emits on every seek.
     * No suspend .first() inside combine — clean coroutine usage.
     */
    val sessionScans: Flow<List<WifiScanResult>> = combine(
        _selectedSessionId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repo.getSessionScansFlow(id)
        },
        _playbackPosition
    ) { allScans, pos ->
        if (allScans.isEmpty()) return@combine emptyList()
        val tMin = allScans.minOf { it.timestamp }
        val tMax = allScans.maxOf { it.timestamp }
        val cutoff = tMin + ((tMax - tMin) * pos).toLong()
        allScans.filter { it.timestamp <= cutoff }.distinctBy { it.bssid }
    }

    fun selectSession(id: String?) { _selectedSessionId.value = id }
    fun setPosition(p: Float)  { _playbackPosition.value = p }
    fun setSpeed(s: Float)     { _playbackSpeed.value = s }
    fun togglePlay()           { _isPlaying.value = !_isPlaying.value }
    fun toggleDiff()           { _diffMode.value = !_diffMode.value }

    fun getSessionScans(sessionId: String) = repo.getSessionScansFlow(sessionId)
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

/**
 * Deterministic virtual 2-D position for a network when no AR position data exists.
 * Distributes APs on a radius-jittered ring using each AP's BSSID hash for stability.
 */
fun virtualPosition(bssid: String, index: Int, total: Int): Pair<Float, Float> {
    val angle = if (total <= 1) 0.0 else (2.0 * PI * index / total)
    val seed  = (bssid.hashCode() and 0xFFFF).toDouble() / 0xFFFF.toDouble()
    val r     = 3.0 + seed * 4.0   // 3–7 m radius
    return (r * cos(angle)).toFloat() to (r * sin(angle)).toFloat()
}
