package com.example.bbcsoundapplication

import android.content.Context
import android.media.MediaPlayer

class StaticEffect (val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    /**
     *  Initialise media player and start playing
     */
    fun startPlaying() {
        // Initialise media player and start it
        mediaPlayer = MediaPlayer.create(context, R.raw.static_effect)
        mediaPlayer?.apply {
            start()
            setLooping(true)
        }
    }

    /**
     * Set media player volume
     */
    fun setVolume(volume: Float) {
        // Set the volume for left and right sound output
        mediaPlayer?.setVolume(volume, volume)
    }

    /**
     * Stop playing and release media player
     */
    fun stopPlaying() {
        // Release and nullify media player
        mediaPlayer?.release()
        mediaPlayer = null
    }

    /**
     * Return whether media player exists
     */
    fun isMediaPlayerNull(): Boolean = (mediaPlayer == null)
}