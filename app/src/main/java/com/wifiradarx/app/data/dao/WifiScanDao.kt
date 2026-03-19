package com.wifiradarx.app.data.dao

import androidx.room.*
import com.wifiradarx.app.data.entity.WifiScanResult
import kotlinx.coroutines.flow.Flow

@Dao
interface WifiScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: WifiScanResult): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<WifiScanResult>)

    @Query("SELECT * FROM wifi_scan_results ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<WifiScanResult>>

    @Query("SELECT * FROM wifi_scan_results WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getBySessionFlow(sessionId: String): Flow<List<WifiScanResult>>

    @Query("SELECT * FROM wifi_scan_results WHERE bssid = :bssid ORDER BY timestamp DESC LIMIT :limit")
    fun getByBssidFlow(bssid: String, limit: Int = 100): Flow<List<WifiScanResult>>

    @Query("SELECT * FROM wifi_scan_results WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getSinceFlow(since: Long): Flow<List<WifiScanResult>>

    @Query("SELECT DISTINCT bssid, ssid FROM wifi_scan_results")
    fun getAllDistinctNetworksFlow(): Flow<List<BssidSsidTuple>>

    @Query("SELECT * FROM wifi_scan_results WHERE session_id = :sessionId AND bssid = :bssid ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForSession(sessionId: String, bssid: String): WifiScanResult?

    @Query("SELECT AVG(rssi) FROM wifi_scan_results WHERE bssid = :bssid AND timestamp >= :since")
    suspend fun getAverageRssi(bssid: String, since: Long): Float?

    @Query("SELECT COUNT(*) FROM wifi_scan_results WHERE session_id = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    @Query("DELETE FROM wifi_scan_results WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM wifi_scan_results WHERE session_id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("SELECT * FROM wifi_scan_results WHERE is_rogue_suspect = 1 ORDER BY timestamp DESC")
    fun getRogueSuspectsFlow(): Flow<List<WifiScanResult>>

    @Query("SELECT * FROM wifi_scan_results WHERE session_id = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatestForSession(sessionId: String, limit: Int): List<WifiScanResult>

    data class BssidSsidTuple(val bssid: String, val ssid: String)
}
