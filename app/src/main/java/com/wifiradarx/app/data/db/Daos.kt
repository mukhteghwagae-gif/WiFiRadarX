package com.wifiradarx.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WifiScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanResult(result: WifiScanResult): Long

    @Query("SELECT * FROM wifi_scan_results WHERE sessionId = :sessionId ORDER BY timestampMicros DESC")
    fun getScanResultsBySession(sessionId: Long): Flow<List<WifiScanResult>>

    @Query("SELECT * FROM wifi_scan_results ORDER BY timestampMicros DESC")
    fun getAllScanResults(): Flow<List<WifiScanResult>>

    @Query("SELECT * FROM wifi_scan_results WHERE bssid = :bssid ORDER BY timestampMicros DESC LIMIT :limit")
    fun getScanResultsByBssid(bssid: String, limit: Int = 50): Flow<List<WifiScanResult>>

    @Query("DELETE FROM wifi_scan_results WHERE sessionId = :sessionId")
    suspend fun deleteSessionResults(sessionId: Long)

    @Query("DELETE FROM wifi_scan_results WHERE timestampMicros < :cutoffMicros")
    suspend fun pruneOldResults(cutoffMicros: Long)
}

@Dao
interface DeviceFingerprintDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFingerprint(fingerprint: DeviceFingerprint)

    @Query("SELECT * FROM device_fingerprints WHERE bssid = :bssid")
    suspend fun getFingerprint(bssid: String): DeviceFingerprint?

    @Query("SELECT * FROM device_fingerprints")
    fun getAllFingerprints(): Flow<List<DeviceFingerprint>>

    @Delete
    suspend fun deleteFingerprint(fingerprint: DeviceFingerprint)
}

@Dao
interface TrustedBssidDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrustedBssid(profile: TrustedBssidProfile)

    @Query("SELECT * FROM trusted_bssid_profiles WHERE bssid = :bssid")
    suspend fun getTrustedBssid(bssid: String): TrustedBssidProfile?

    @Query("SELECT * FROM trusted_bssid_profiles")
    fun getAllTrustedBssids(): Flow<List<TrustedBssidProfile>>

    @Delete
    suspend fun deleteTrustedBssid(profile: TrustedBssidProfile)
}

@Dao
interface HourlyBaselineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseline(baseline: HourlyBaseline)

    @Query("SELECT * FROM hourly_baselines WHERE bssid = :bssid AND hourOfDay = :hour")
    suspend fun getBaseline(bssid: String, hour: Int): HourlyBaseline?

    @Query("SELECT * FROM hourly_baselines WHERE bssid = :bssid")
    fun getBaselinesByBssid(bssid: String): Flow<List<HourlyBaseline>>

    @Query("SELECT * FROM hourly_baselines")
    fun getAllBaselines(): Flow<List<HourlyBaseline>>
}

@Dao
interface SignalSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: SignalSample)

    @Query("SELECT * FROM signal_samples WHERE sessionId = :sessionId")
    fun getSamplesBySession(sessionId: Long): Flow<List<SignalSample>>

    @Query("SELECT * FROM signal_samples WHERE bssid = :bssid AND sessionId = :sessionId")
    fun getSamplesByBssidAndSession(bssid: String, sessionId: Long): Flow<List<SignalSample>>

    @Query("SELECT * FROM signal_samples WHERE sessionId = :sessionId AND posX != 0 AND posZ != 0")
    suspend fun getLocatedSamplesBySession(sessionId: Long): List<SignalSample>
}

@Dao
interface SessionMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionMetadata): Long

    @Update
    suspend fun updateSession(session: SessionMetadata)

    @Query("SELECT * FROM session_metadata ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionMetadata>>

    @Query("SELECT * FROM session_metadata WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): SessionMetadata?

    @Delete
    suspend fun deleteSession(session: SessionMetadata)
}
