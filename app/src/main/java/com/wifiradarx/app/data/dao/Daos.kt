package com.wifiradarx.app.data.dao

import androidx.room.*
import com.wifiradarx.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceFingerprintDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fp: DeviceFingerprint): Long

    @Update
    suspend fun update(fp: DeviceFingerprint)

    @Query("SELECT * FROM device_fingerprints WHERE bssid = :bssid LIMIT 1")
    suspend fun getByBssid(bssid: String): DeviceFingerprint?

    @Query("SELECT * FROM device_fingerprints ORDER BY last_seen DESC")
    fun getAllFlow(): Flow<List<DeviceFingerprint>>

    @Query("SELECT * FROM device_fingerprints WHERE mac_randomized = 1")
    fun getMacRandomizedFlow(): Flow<List<DeviceFingerprint>>

    @Query("DELETE FROM device_fingerprints WHERE bssid = :bssid")
    suspend fun deleteByBssid(bssid: String)
}

@Dao
interface TrustedBssidDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: TrustedBssidProfile): Long

    @Query("SELECT * FROM trusted_bssid_profiles WHERE bssid = :bssid LIMIT 1")
    suspend fun getByBssid(bssid: String): TrustedBssidProfile?

    @Query("SELECT * FROM trusted_bssid_profiles ORDER BY added_at DESC")
    fun getAllFlow(): Flow<List<TrustedBssidProfile>>

    @Query("SELECT * FROM trusted_bssid_profiles WHERE is_trusted = 1")
    fun getTrustedFlow(): Flow<List<TrustedBssidProfile>>

    @Query("DELETE FROM trusted_bssid_profiles WHERE bssid = :bssid")
    suspend fun deleteByBssid(bssid: String)
}

@Dao
interface HourlyBaselineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(baseline: HourlyBaseline): Long

    @Update
    suspend fun update(baseline: HourlyBaseline)

    @Query("SELECT * FROM hourly_baselines WHERE bssid = :bssid AND hour_of_week = :hour LIMIT 1")
    suspend fun getBaseline(bssid: String, hour: Int): HourlyBaseline?

    @Query("SELECT * FROM hourly_baselines WHERE bssid = :bssid ORDER BY hour_of_week ASC")
    fun getBaselineForBssidFlow(bssid: String): Flow<List<HourlyBaseline>>

    @Query("DELETE FROM hourly_baselines WHERE bssid = :bssid")
    suspend fun deleteForBssid(bssid: String)
}

@Dao
interface SignalSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: SignalSample): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<SignalSample>)

    @Query("SELECT * FROM signal_samples WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getBySessionFlow(sessionId: String): Flow<List<SignalSample>>

    @Query("SELECT * FROM signal_samples WHERE bssid = :bssid ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByBssid(bssid: String, limit: Int = 200): List<SignalSample>

    @Query("DELETE FROM signal_samples WHERE session_id = :sessionId")
    suspend fun deleteSession(sessionId: String)
}

@Dao
interface SessionMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionMetadata)

    @Update
    suspend fun update(session: SessionMetadata)

    @Query("SELECT * FROM session_metadata ORDER BY start_time DESC")
    fun getAllFlow(): Flow<List<SessionMetadata>>

    @Query("SELECT * FROM session_metadata WHERE sessionId = :id LIMIT 1")
    suspend fun getById(id: String): SessionMetadata?

    @Query("SELECT * FROM session_metadata ORDER BY start_time DESC LIMIT 1")
    suspend fun getLatest(): SessionMetadata?

    @Query("DELETE FROM session_metadata WHERE sessionId = :id")
    suspend fun deleteById(id: String)
}
