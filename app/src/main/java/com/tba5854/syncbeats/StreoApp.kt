package com.tba5854.syncbeats

import android.app.Application
import com.tba5854.syncbeats.core.storage.MusicStorage
import com.tba5854.syncbeats.settings.Settings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StreoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        MusicStorage.init(this)
    }
}