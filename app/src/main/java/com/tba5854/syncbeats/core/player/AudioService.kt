package com.tba5854.syncbeats.core.player

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AudioService : MediaSessionService() {

    @Inject lateinit var playerManager: ExoPlayerManager

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return playerManager.session
    }

    override fun onDestroy() {
        super.onDestroy()
        // Do not release playerManager here, it is a Singleton tied to App lifecycle.
    }
}
