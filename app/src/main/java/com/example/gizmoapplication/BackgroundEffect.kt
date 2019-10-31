package com.example.gizmoapplication

import android.content.Context
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log

class BackgroundEffect (private val context: Context) {

    private val mediaPlayer: MediaPlayer

    init {
        // Initialise the media player, set on prepared listener and call to prepare/start
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener { onPrepared(it) }
        }
        startSilently()
    }

    /**
     * Load resource and start preparing
     */
    fun startSilently() {

        // Start playing background sound resource
        context.resources.openRawResourceFd(R.raw.soundbed)?.let { assetFileDescriptor ->
            mediaPlayer.run {
                setDataSource(assetFileDescriptor)
                prepareAsync()
            }
        }
    }

    /**
     * Called once the resource is prepared, should start playing with no volume
     */
    fun onPrepared(mediaPlayer: MediaPlayer) {

        mediaPlayer.apply {
            setVolume(0f, 0f)
            start()
            isLooping = true
        }
    }

    /**
     * Stop playback, player would have to prepare to start again
     */
    fun stop() {

        mediaPlayer.stop()
    }

    /**
     * Continues playback when paused
     */
    fun play() {

        mediaPlayer.start()
    }

    /**
     * Pauses playback
     */
    fun pause() {

        mediaPlayer.pause()
    }

    /**
     * Set the sound volume
     */
    fun setVolume(volume: Float) {

        mediaPlayer.setVolume(volume, volume)
    }
}