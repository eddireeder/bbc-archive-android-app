package com.example.thesonosynthesiserapplication

import android.media.MediaPlayer
import android.util.Log

class MediaPlayerPool(maxMediaPlayers: Int) {

    private val mediaPlayerPool = mutableListOf<MediaPlayerWithState>().also {
        for (i in 0 until maxMediaPlayers) it += MediaPlayerWithState(MediaPlayer(), false)
    }

    init {
        for (mediaPlayerWithState in mediaPlayerPool) {
            mediaPlayerWithState.mediaPlayer.setOnPreparedListener {
                Log.i("Media", "Player prepared")
                mediaPlayerWithState.prepared = true
                it.setVolume(0f, 0f)
                it.start()
                it.isLooping = true
            }
        }
    }

    /**
     * Build and return a media player
     */
    private fun buildPlayer() = MediaPlayer().apply {
        setOnPreparedListener {
            // Record state
            setPrepared(it, true)
            // Start with 0 volume
            setVolume(0f, 0f)
            start()
            isLooping = true
        }
    }

    /**
     * Searches for MediaPlayerWithState using MediaPlayer and sets its state
     */
    fun setPrepared(mediaPlayer: MediaPlayer, prepared: Boolean) {
        for (mediaPlayerWithState in mediaPlayerPool) {
            if (mediaPlayerWithState.mediaPlayer == mediaPlayer) {
                mediaPlayerWithState.prepared = prepared
                break
            }
        }
    }

    /**
     * Returns a media player (with state) if one is available
     */
    fun requestPlayer(): MediaPlayerWithState? {
        return if (mediaPlayerPool.isNotEmpty()) {
            mediaPlayerPool.removeAt(0)
        } else null
    }

    /**
     * Recycle a media player (with state) for reuse
     */
    fun recyclePlayer(mediaPlayerWithState: MediaPlayerWithState) {
        mediaPlayerWithState.prepared = false
        mediaPlayerWithState.mediaPlayer.reset()
        mediaPlayerPool += mediaPlayerWithState
    }
}