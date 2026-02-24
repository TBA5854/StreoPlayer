package com.tba5854.syncbeats.core.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class ExoPlayerManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    var onNextCallback: (() -> Unit)? = null
    var onPrevCallback: (() -> Unit)? = null

    private val forwardingPlayer =
            object : ForwardingPlayer(player) {
                override fun seekToNext() {
                    onNextCallback?.invoke()
                }
                override fun seekToNextMediaItem() {
                    onNextCallback?.invoke()
                }
                override fun seekToPrevious() {
                    onPrevCallback?.invoke()
                }
                override fun seekToPreviousMediaItem() {
                    onPrevCallback?.invoke()
                }
                override fun getAvailableCommands(): Player.Commands {
                    return super.getAvailableCommands()
                            .buildUpon()
                            .add(Player.COMMAND_SEEK_TO_NEXT)
                            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                            .build()
                }
            }

    val session: MediaSession = MediaSession.Builder(context, forwardingPlayer).build()
    private val handler = Handler(Looper.getMainLooper())

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow

    init {
        player.addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlayingFlow.value = isPlaying
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        _isPlayingFlow.value = player.isPlaying
                    }
                }
        )
    }

    fun loadFile(file: File, title: String? = null) {
        val displayTitle = title ?: file.nameWithoutExtension
        val metadata = MediaMetadata.Builder().setTitle(displayTitle).build()
        val mediaItem =
                MediaItem.Builder().setUri(Uri.fromFile(file)).setMediaMetadata(metadata).build()
        player.setMediaItem(mediaItem)
        player.prepare()
    }

    fun onReady(callback: () -> Unit) {
        player.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) callback()
                    }
                }
        )
    }

    fun loadUri(uri: Uri, title: String? = null) {
        val builder = MediaItem.Builder().setUri(uri)
        if (title != null) {
            builder.setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        }
        player.setMediaItem(builder.build())
        player.prepare()
    }

    fun play() {
        player.playWhenReady = true
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, AudioService::class.java))
        }
    }

    fun playAtTime(timeMs: Long) {
        val delay = timeMs - System.currentTimeMillis()
        if (delay > 0) {
            handler.postDelayed({ play() }, delay)
        } else {
            play()
        }
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        player.play()
    }

    fun stop() {
        player.stop()
    }

    fun stopAndClear() {
        player.stop()
        player.clearMediaItems()
    }

    fun isPlaying(): Boolean = player.isPlaying

    fun getCurrentPositionMs(): Long = player.currentPosition

    fun getCurrentPositionSec(): Double = player.currentPosition / 1000.0

    fun getDurationMs(): Long = player.duration

    fun getDurationSec(): Double = if (player.duration > 0) player.duration / 1000.0 else 0.0

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun seekToSec(positionSec: Double) {
        player.seekTo((positionSec * 1000).toLong())
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        session.release()
        player.release()
    }
}
