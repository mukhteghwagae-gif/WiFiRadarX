package com.wifiradarx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wifi_scan_results")
data class WifiScanResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val vendorOui: String,
    val timestampMicros: Long,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val posZ: Float = 0f,
    val sessionId: Long
)

@Entity(tableName = "device_fingerprints")
data class DeviceFingerprint(
    @PrimaryKey val bssid: String,
    val featureVector: String, // JSON-encoded feature vector
    val lastSeen: Long,
    val similarityScore: Double,
    val isMacRandomized: Boolean
)

@Entity(tableName = "trusted_bssid_profiles")
data class TrustedBssidProfile(
    @PrimaryKey val bssid: String,
    val ssid: String,
    val addedAt: Long,
    val expectedSecurity: String
)

@Entity(tableName = "hourly_baselines")
data class HourlyBaseline(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bssid: String,
    val hourOfDay: Int,
    val avgRssi: Double,
    val sampleCount: Int
)

@Entity(tableName = "signal_samples")
data class SignalSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bssid: String,
    val rssi: Int,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val timestamp: Long,
    val sessionId: Long
)

@Entity(tableName = "session_metadata")
data class SessionMetadata(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val name: String,
    val description: String? = null
)
