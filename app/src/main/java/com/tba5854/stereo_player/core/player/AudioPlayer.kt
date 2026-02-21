package com.tba5854.stereo_player.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class ExoPlayerManager(private val context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    fun play() {
        val fileUri = Uri.fromFile(File("/storage/emulated/0/music.mp3")) // turn File -> Uri
        val mediaItem = MediaItem.fromUri(fileUri)
//        exoPlayer.setMediaItem(mediaItem)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

fun play(timeMs: Long) {
        val delay = timeMs - System.currentTimeMillis()
        if (delay > 0) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                play()
            }, delay)
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

    fun isPlaying(): Boolean {
        return player.isPlaying
    }

    fun getCurrentPosition(): Long {
        return player.currentPosition
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun release() {
        player.release()
    }
}
