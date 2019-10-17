package com.example.gizmoapplication

import android.media.MediaPlayer

class MediaPlayerPool(maxStreams: Int) {

    private val mediaPlayerPool = mutableListOf<MediaPlayer>().also {
        for (i in 0..maxStreams) it += buildPlayer()
    }

    /**
     * Build and return a media player
     */
    private fun buildPlayer() = MediaPlayer().apply {
        setOnPreparedListener {
            // Start with 0 volume
            setVolume(0f, 0f)
            start()
        }
        setOnCompletionListener { recyclePlayer(it) }
    }

    /**
     * Returns a media player if one is available
     */
    fun requestPlayer(): MediaPlayer? {
        return if (mediaPlayerPool.isNotEmpty()) {
            mediaPlayerPool.removeAt(0)
        } else null
    }

    /**
     * Recycle a media player for reuse
     */
    fun recyclePlayer(mediaPlayer: MediaPlayer) {
        mediaPlayer.reset()
        mediaPlayerPool += mediaPlayer
    }
}