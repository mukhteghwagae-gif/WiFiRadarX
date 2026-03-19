package com.wifiradarx.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wifiradarx.app.data.dao.*
import com.wifiradarx.app.data.entity.*

@Database(
    entities = [
        WifiScanResult::class,
        DeviceFingerprint::class,
        TrustedBssidProfile::class,
        HourlyBaseline::class,
        SignalSample::class,
        SessionMetadata::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WiFiRadarDatabase : RoomDatabase() {
    abstract fun wifiScanDao(): WifiScanDao
    abstract fun deviceFingerprintDao(): DeviceFingerprintDao
    abstract fun trustedBssidDao(): TrustedBssidDao
    abstract fun hourlyBaselineDao(): HourlyBaselineDao
    abstract fun signalSampleDao(): SignalSampleDao
    abstract fun sessionMetadataDao(): SessionMetadataDao

    companion object {
        @Volatile private var INSTANCE: WiFiRadarDatabase? = null

        fun getInstance(context: Context): WiFiRadarDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WiFiRadarDatabase::class.java,
                    "wifiradarx.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
