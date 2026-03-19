package com.wifiradarx.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_fingerprints",
    indices = [Index(value = ["bssid"], unique = true)]
)
data class DeviceFingerprint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bssid: String,
    val ssid: String,
    @ColumnInfo(name = "feature_vector") val featureVector: String, // JSON array of 12 floats
    @ColumnInfo(name = "beacon_interval_ms") val beaconIntervalMs: Float,
    @ColumnInfo(name = "clock_drift_ppm") val clockDriftPpm: Float,
    @ColumnInfo(name = "iat_mean") val iatMean: Float,
    @ColumnInfo(name = "iat_variance") val iatVariance: Float,
    @ColumnInfo(name = "iat_skewness") val iatSkewness: Float,
    @ColumnInfo(name = "iat_kurtosis") val iatKurtosis: Float,
    @ColumnInfo(name = "probe_burst_count") val probeBurstCount: Int,
    @ColumnInfo(name = "mac_randomized") val macRandomized: Boolean = false,
    @ColumnInfo(name = "first_seen") val firstSeen: Long,
    @ColumnInfo(name = "last_seen") val lastSeen: Long,
    @ColumnInfo(name = "observation_count") val observationCount: Int = 1
)

@Entity(
    tableName = "trusted_bssid_profiles",
    indices = [Index(value = ["bssid"], unique = true)]
)
data class TrustedBssidProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bssid: String,
    val ssid: String,
    @ColumnInfo(name = "expected_channel") val expectedChannel: Int,
    @ColumnInfo(name = "expected_frequency") val expectedFrequency: Int,
    @ColumnInfo(name = "expected_capabilities") val expectedCapabilities: String,
    @ColumnInfo(name = "vendor_prefix") val vendorPrefix: String,
    @ColumnInfo(name = "is_trusted") val isTrusted: Boolean = true,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "hourly_baselines")
data class HourlyBaseline(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bssid: String,
    @ColumnInfo(name = "hour_of_week") val hourOfWeek: Int, // 0-167
    @ColumnInfo(name = "ema_rssi") val emaRssi: Float,
    @ColumnInfo(name = "ema_variance") val emaVariance: Float,
    @ColumnInfo(name = "sample_count") val sampleCount: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "signal_samples")
data class SignalSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val bssid: String,
    val rssi: Int,
    @ColumnInfo(name = "pos_x") val posX: Float,
    @ColumnInfo(name = "pos_y") val posY: Float,
    @ColumnInfo(name = "pos_z") val posZ: Float,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "session_metadata")
data class SessionMetadata(
    @PrimaryKey val sessionId: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long = 0L,
    @ColumnInfo(name = "scan_count") val scanCount: Int = 0,
    @ColumnInfo(name = "ap_count") val apCount: Int = 0,
    @ColumnInfo(name = "area_sq_meters") val areaSqMeters: Float = 0f,
    @ColumnInfo(name = "floor_level") val floorLevel: Int = 0,
    val notes: String = "",
    @ColumnInfo(name = "export_path") val exportPath: String = ""
)
