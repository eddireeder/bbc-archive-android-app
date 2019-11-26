package com.example.thesonosynthesiserapplication

import android.content.Context
import android.media.MediaPlayer

class BackgroundEffect (private val context: Context) {

    private val mediaPlayer: MediaPlayer

    init {
        // Initialise the media player
        mediaPlayer = MediaPlayer()
    }

    /**
     * Called once the playing background resource is prepared, starts playing with no volume
     */
    fun onPreparedPlaying(mediaPlayer: MediaPlayer) {

        mediaPlayer.apply {
            setVolume(0f, 0f)
            start()
            isLooping = true
        }
    }

    /**
     * Called once the idle background resource is prepared, starts playing with full volume
     */
    fun onPreparedIdle(mediaPlayer: MediaPlayer) {

        mediaPlayer.apply {
            setVolume(1f, 1f)
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

    /**
     * Start playing the idle background sound (at 0 volume)
     */
    fun startIdleBackground() {

        // Recycle player
        mediaPlayer.reset()

        // Set media player on prepared listener to onPreparedIdle
        mediaPlayer.apply {
            setOnPreparedListener { onPreparedIdle(it) }
        }

        // Start playing background sound resource
        context.resources.openRawResourceFd(R.raw.idle_background)?.let { assetFileDescriptor ->
            mediaPlayer.run {
                setDataSource(assetFileDescriptor)
                prepareAsync()
            }
        }
    }

    /**
     * Start playing the playing background sound (at 0 volume)
     */
    fun startPlayingBackground() {

        // Recycle player
        mediaPlayer.reset()

        // Set media player on prepared listener to onPreparedPlaying
        mediaPlayer.apply {
            setOnPreparedListener { onPreparedPlaying(it) }
        }

        // Start playing background sound resource
        context.resources.openRawResourceFd(R.raw.playing_background)?.let { assetFileDescriptor ->
            mediaPlayer.run {
                setDataSource(assetFileDescriptor)
                prepareAsync()
            }
        }
    }
}