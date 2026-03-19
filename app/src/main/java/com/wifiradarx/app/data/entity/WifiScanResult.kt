package com.wifiradarx.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "wifi_scan_results",
    indices = [
        Index(value = ["bssid"]),
        Index(value = ["session_id"]),
        Index(value = ["timestamp"])
    ]
)
data class WifiScanResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,

    @ColumnInfo(name = "capabilities")
    val capabilities: String,

    @ColumnInfo(name = "vendor_oui")
    val vendorOui: String,

    @ColumnInfo(name = "timestamp_micros")
    val timestampMicros: Long,

    val timestamp: Long,

    @ColumnInfo(name = "pos_x")
    val posX: Float = 0f,

    @ColumnInfo(name = "pos_y")
    val posY: Float = 0f,

    @ColumnInfo(name = "pos_z")
    val posZ: Float = 0f,

    @ColumnInfo(name = "security_score")
    val securityScore: Int = 0,

    @ColumnInfo(name = "is_rogue_suspect")
    val isRogueSuspect: Boolean = false,

    @ColumnInfo(name = "threat_score")
    val threatScore: Int = 0,

    @ColumnInfo(name = "is_5ghz")
    val is5GHz: Boolean = false,

    @ColumnInfo(name = "is_6ghz")
    val is6GHz: Boolean = false,

    @ColumnInfo(name = "center_freq_0")
    val centerFreq0: Int = 0,

    @ColumnInfo(name = "center_freq_1")
    val centerFreq1: Int = 0,

    @ColumnInfo(name = "channel_width")
    val channelWidth: Int = 0
)
