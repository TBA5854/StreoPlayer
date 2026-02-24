package com.tba5854.syncbeats

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.tba5854.syncbeats.core.player.AudioService
import com.tba5854.syncbeats.settings.Settings
import com.tba5854.syncbeats.ui.navigation.AppNav
import com.tba5854.syncbeats.ui.theme.StreoPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow

object IntentHandler {
    val pendingAudioUris = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        val sessionToken = SessionToken(this, ComponentName(this, AudioService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        handleIntent(intent)
        setContent { StreoPlayerTheme { AppNav() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri =
                when (intent.action) {
                    Intent.ACTION_VIEW -> intent.data
                    Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                    else -> null
                }
        uri?.let { IntentHandler.pendingAudioUris.tryEmit(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
