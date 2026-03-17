package com.wifiradarx.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
abstract class AppDatabase : RoomDatabase() {
    abstract fun wifiScanDao(): WifiScanDao
    abstract fun deviceFingerprintDao(): DeviceFingerprintDao
    abstract fun trustedBssidDao(): TrustedBssidDao
    abstract fun hourlyBaselineDao(): HourlyBaselineDao
    abstract fun signalSampleDao(): SignalSampleDao
    abstract fun sessionMetadataDao(): SessionMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wifiradarx_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
