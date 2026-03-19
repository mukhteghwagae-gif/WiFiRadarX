package com.wifiradarx.app

import android.app.Application
import com.wifiradarx.app.data.db.AppDatabase
import com.wifiradarx.app.data.repository.WifiRepository

class WiFiRadarXApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { WifiRepository(database) }
}
