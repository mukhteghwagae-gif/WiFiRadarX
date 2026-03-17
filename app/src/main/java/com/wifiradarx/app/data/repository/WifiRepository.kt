package com.wifiradarx.app.data.repository

import com.wifiradarx.app.data.db.*
import kotlinx.coroutines.flow.Flow

class WifiRepository(private val db: AppDatabase) {

    // ── Scan Results ──────────────────────────────────────────────────────────

    fun getAllScanResults(): Flow<List<WifiScanResult>> =
        db.wifiScanDao().getAllScanResults()

    fun getScanResultsBySession(sessionId: Long): Flow<List<WifiScanResult>> =
        db.wifiScanDao().getScanResultsBySession(sessionId)

    fun getScanResultsByBssid(bssid: String): Flow<List<WifiScanResult>> =
        db.wifiScanDao().getScanResultsByBssid(bssid)

    suspend fun insertScanResult(result: WifiScanResult): Long =
        db.wifiScanDao().insertScanResult(result)

    suspend fun pruneOldResults(cutoffMicros: Long) =
        db.wifiScanDao().pruneOldResults(cutoffMicros)

    // ── Sessions ──────────────────────────────────────────────────────────────

    fun getAllSessions(): Flow<List<SessionMetadata>> =
        db.sessionMetadataDao().getAllSessions()

    suspend fun insertSession(session: SessionMetadata): Long =
        db.sessionMetadataDao().insertSession(session)

    suspend fun updateSession(session: SessionMetadata) =
        db.sessionMetadataDao().updateSession(session)

    suspend fun getSessionById(sessionId: Long): SessionMetadata? =
        db.sessionMetadataDao().getSessionById(sessionId)

    suspend fun deleteSession(session: SessionMetadata) =
        db.sessionMetadataDao().deleteSession(session)

    // ── Device Fingerprints ───────────────────────────────────────────────────

    suspend fun insertFingerprint(fingerprint: DeviceFingerprint) =
        db.deviceFingerprintDao().insertFingerprint(fingerprint)

    fun getAllFingerprints(): Flow<List<DeviceFingerprint>> =
        db.deviceFingerprintDao().getAllFingerprints()

    suspend fun getFingerprint(bssid: String): DeviceFingerprint? =
        db.deviceFingerprintDao().getFingerprint(bssid)

    // ── Trusted BSSIDs ────────────────────────────────────────────────────────

    suspend fun insertTrustedBssid(profile: TrustedBssidProfile) =
        db.trustedBssidDao().insertTrustedBssid(profile)

    fun getAllTrustedBssids(): Flow<List<TrustedBssidProfile>> =
        db.trustedBssidDao().getAllTrustedBssids()

    suspend fun getTrustedBssid(bssid: String): TrustedBssidProfile? =
        db.trustedBssidDao().getTrustedBssid(bssid)

    // ── Hourly Baselines ──────────────────────────────────────────────────────

    suspend fun insertBaseline(baseline: HourlyBaseline) =
        db.hourlyBaselineDao().insertBaseline(baseline)

    fun getAllBaselines(): Flow<List<HourlyBaseline>> =
        db.hourlyBaselineDao().getAllBaselines()

    suspend fun getBaseline(bssid: String, hour: Int): HourlyBaseline? =
        db.hourlyBaselineDao().getBaseline(bssid, hour)

    // ── Signal Samples ────────────────────────────────────────────────────────

    suspend fun insertSample(sample: SignalSample) =
        db.signalSampleDao().insertSample(sample)

    fun getSamplesBySession(sessionId: Long): Flow<List<SignalSample>> =
        db.signalSampleDao().getSamplesBySession(sessionId)

    fun getSamplesByBssidAndSession(bssid: String, sessionId: Long): Flow<List<SignalSample>> =
        db.signalSampleDao().getSamplesByBssidAndSession(bssid, sessionId)

    suspend fun getLocatedSamplesBySession(sessionId: Long): List<SignalSample> =
        db.signalSampleDao().getLocatedSamplesBySession(sessionId)
}
