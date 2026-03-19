package com.wifiradarx.app.ui.viewmodel

import android.app.Application
import android.net.wifi.ScanResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wifiradarx.app.WiFiRadarXApp
import com.wifiradarx.app.data.entity.SessionMetadata
import com.wifiradarx.app.data.entity.WifiScanResult
import com.wifiradarx.app.intelligence.ChannelAnalyzer
import com.wifiradarx.app.intelligence.SignalIntelligenceEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val wfxApp  = app as WiFiRadarXApp
    private val repo    = wfxApp.wifiRepository
    private val engine  = wfxApp.intelligenceEngine

    val scanResults: StateFlow<List<ScanResult>> = repo.scanResultsFlow
    val isScanning:  StateFlow<Boolean>           = repo.isScanning
    val allScans:    Flow<List<WifiScanResult>>   = repo.getAllScansFlow()
    val sessions:    Flow<List<SessionMetadata>>  = repo.getAllSessionsFlow()
    val rogues:      Flow<List<WifiScanResult>>   = repo.getRogueSuspectsFlow()

    private val _insightReport =
        MutableStateFlow<SignalIntelligenceEngine.InsightReport?>(null)
    val insightReport: StateFlow<SignalIntelligenceEngine.InsightReport?> = _insightReport

    fun startScanning() {
        repo.startScanning { rawResults ->
            viewModelScope.launch {
                repo.saveScanResults(rawResults)
                val hour = Calendar.getInstance().let {
                    it.get(Calendar.DAY_OF_WEEK) * 24 + it.get(Calendar.HOUR_OF_DAY)
                }
                val snapshots = rawResults.map { sr ->
                    SignalIntelligenceEngine.ScanSnapshot(
                        bssid           = sr.BSSID      ?: "",
                        ssid            = sr.SSID       ?: "",
                        rssi            = sr.level,
                        frequency       = sr.frequency,
                        channel         = ChannelAnalyzer.frequencyToChannel(sr.frequency),
                        capabilities    = sr.capabilities ?: "",
                        vendorOui       = "",
                        posX            = 0f,
                        posY            = 0f,
                        posZ            = 0f,
                        timestampMicros = sr.timestamp
                    )
                }
                _insightReport.value = engine.processScan(snapshots, hour)
            }
        }
    }

    fun stopScanning()         = repo.stopScanning()
    fun triggerScan(): Boolean = repo.triggerScan()
    fun isWifiEnabled()        = repo.isWifiEnabled()
    fun getCurrentSessionId()  = repo.getCurrentSessionId()
    fun startNewSession()      = repo.startSession()
}
