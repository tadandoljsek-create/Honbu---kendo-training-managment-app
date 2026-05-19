package com.honbu.app.util

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.honbu.app.R
import com.honbu.app.data.preferences.SoundConfig
import com.honbu.app.data.preferences.SoundType

class SoundManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    fun playTimerEnd(config: SoundConfig) =
        play(config.timerEndSound, config.customTimerEndUri)

    fun playIntervalChange(config: SoundConfig) =
        play(config.intervalChangeSound, config.customIntervalChangeUri)

    fun playIntervalStart(config: SoundConfig) =
        play(config.intervalStartSound, config.customIntervalStartUri)

    fun previewSound(type: SoundType, customUri: String = "") = play(type, customUri)

    private fun play(type: SoundType, customUri: String) {
        stopCurrent()
        val resId = when (type) {
            SoundType.BEEP        -> R.raw.sound_beep
            SoundType.WHISTLE     -> R.raw.whistle
            SoundType.GONG        -> R.raw.gong
            SoundType.START_PISTOL-> R.raw.start_pistol
            SoundType.YAME        -> R.raw.yame
            SoundType.TIME        -> R.raw.time
            SoundType.AIR_HORN    -> R.raw.air_horn
            SoundType.BELL        -> R.raw.bell
            SoundType.SHIP_HORN   -> R.raw.ship_horn
            SoundType.HAJIME      -> R.raw.hajime
            SoundType.CUSTOM      -> { if (customUri.isNotEmpty()) playUri(Uri.parse(customUri)); return }
            SoundType.NONE        -> return
        }
        playRaw(resId)
    }

    private fun playRaw(resId: Int) {
        try {
            mediaPlayer = MediaPlayer.create(context, resId)?.apply {
                start()
                setOnCompletionListener { release() }
            }
        } catch (_: Exception) {}
    }

    private fun playUri(uri: Uri) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                start()
                setOnCompletionListener { release() }
            }
        } catch (_: Exception) {}
    }

    private fun stopCurrent() {
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun release() { stopCurrent() }
}
