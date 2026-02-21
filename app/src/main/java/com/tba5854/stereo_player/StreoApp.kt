package com.tba5854.stereo_player

import android.app.Application
import com.tba5854.stereo_player.core.storage.MusicStorage
import com.tba5854.stereo_player.settings.Settings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StreoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        MusicStorage.init(this)
    }
}