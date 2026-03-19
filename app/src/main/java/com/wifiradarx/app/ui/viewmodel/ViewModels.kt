package com.wifiradarx.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wifiradarx.app.WiFiRadarXApp
import com.wifiradarx.app.data.entity.WifiScanResult
import com.wifiradarx.app.intelligence.*
import com.wifiradarx.app.utils.AppSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── Analytics ────────────────────────────────────────────────────────────────
class AnalyticsViewModel(app: Application) : AndroidViewModel(app) {
    private val wfxApp = app as WiFiRadarXApp
    private val repo   = wfxApp.wifiRepository

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
    private val repo   = wfxApp.wifiRepository
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
    val optimizedChannels: StateFlow<List<SimulatedAnnealingOptimizer.ApNode>> =
        _optimizedChannels

    fun analyze(scans: List<WifiScanResult>) {
        viewModelScope.launch {
            val pairs = scans.map { it.frequency to it.rssi }
            val stats = ChannelAnalyzer.analyzeChannels(pairs)
            _channelStats.value = stats
            _best24.value = ChannelAnalyzer.getBestChannels(stats, ChannelAnalyzer.Band.GHZ_2_4)
            _best5.value  = ChannelAnalyzer.getBestChannels(stats, ChannelAnalyzer.Band.GHZ_5)

            val apNodes = scans.distinctBy { it.bssid }.map { s ->
                SimulatedAnnealingOptimizer.ApNode(
                    id      = s.bssid,
                    x       = s.posX,
                    y       = s.posZ,
                    band    = ChannelAnalyzer.getBand(s.frequency)
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
    private val wfxApp  = app as WiFiRadarXApp
    private val repo    = wfxApp.wifiRepository
    private val auditor = SecurityAuditor()

    private val _auditResults =
        MutableStateFlow<List<Pair<WifiScanResult, SecurityAuditor.AuditResult>>>(emptyList())
    val auditResults: StateFlow<List<Pair<WifiScanResult, SecurityAuditor.AuditResult>>> =
        _auditResults

    private val _overallRating = MutableStateFlow("")
    val overallRating: StateFlow<String> = _overallRating

    val rogueAlerts = repo.getRogueSuspectsFlow()

    fun audit(scans: List<WifiScanResult>) {
        viewModelScope.launch {
            val results = scans.distinctBy { it.bssid }
                .map { s -> s to auditor.audit(s.ssid, s.capabilities, s.rssi) }
                .sortedBy { it.second.score }
            _auditResults.value = results
            _overallRating.value = auditor.getOverallRating(results.map { it.second.score })
        }
    }
}

// ─── Network List ─────────────────────────────────────────────────────────────
class NetworkListViewModel(app: Application) : AndroidViewModel(app) {
    private val wfxApp = app as WiFiRadarXApp
    private val repo   = wfxApp.wifiRepository

    val scanResults = repo.scanResultsFlow
    val allDbScans  = repo.getAllScansFlow()

    private val _filterBand = MutableStateFlow("ALL")
    val filterBand: StateFlow<String> = _filterBand
    fun setFilter(band: String) { _filterBand.value = band }
}

// ─── Mesh Optimizer ───────────────────────────────────────────────────────────
class MeshViewModel(app: Application) : AndroidViewModel(app) {
    private val wfxApp = app as WiFiRadarXApp
    private val repo   = wfxApp.wifiRepository
    private val engine = wfxApp.intelligenceEngine

    private val _deadZones =
        MutableStateFlow<List<DeadZoneDetector.DeadZone>>(emptyList())
    val deadZones: StateFlow<List<DeadZoneDetector.DeadZone>> = _deadZones

    private val _apRecs =
        MutableStateFlow<List<DeadZoneDetector.ApRecommendation>>(emptyList())
    val apRecs: StateFlow<List<DeadZoneDetector.ApRecommendation>> = _apRecs

    fun compute(scans: List<WifiScanResult>) {
        viewModelScope.launch {
            scans.forEach { s ->
                engine.idwInterpolator.addSample(s.posX, s.posZ, s.rssi.toFloat())
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
    private val wfxApp = app as WiFiRadarXApp
    private val repo   = wfxApp.wifiRepository

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
    private val wfxApp = app as WiFiRadarXApp
    private val repo   = wfxApp.wifiRepository

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
    private val repo         = wfxApp.wifiRepository
    private val triangulator = wfxApp.intelligenceEngine.interferenceTriangulator

    private val _source = MutableStateFlow<InterferenceTriangulator.Source?>(null)
    val source: StateFlow<InterferenceTriangulator.Source?> = _source

    private val _measurementCount = MutableStateFlow(0)
    val measurementCount: StateFlow<Int> = _measurementCount

    fun addMeasurement(x: Float, z: Float, score: Float) {
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
    private val wfxApp = app as WiFiRadarXApp
    private val repo   = wfxApp.wifiRepository

    val sessions = repo.getAllSessionsFlow()

    private val _playbackPosition = MutableStateFlow(0f)
    val playbackPosition: StateFlow<Float> = _playbackPosition

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _diffMode = MutableStateFlow(false)
    val diffMode: StateFlow<Boolean> = _diffMode

    fun setPosition(p: Float)  { _playbackPosition.value = p }
    fun setSpeed(s: Float)     { _playbackSpeed.value = s }
    fun togglePlay()           { _isPlaying.value = !_isPlaying.value }
    fun toggleDiff()           { _diffMode.value = !_diffMode.value }

    fun getSessionScans(sessionId: String) = repo.getSessionScansFlow(sessionId)
}
