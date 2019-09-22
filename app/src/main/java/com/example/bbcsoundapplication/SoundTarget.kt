package com.example.bbcsoundapplication

import android.media.MediaPlayer
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

class SoundTarget (val url: String, val directionVector: FloatArray) : MediaPlayer.OnPreparedListener {

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Initialise media player and prepare to play
     */
    fun startStreaming() {
        // Initialise media player and prepare
        mediaPlayer = MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener(this@SoundTarget)
            prepareAsync()
        }
    }

    /**
     * Called when media player is ready
     */
    override fun onPrepared(mp: MediaPlayer?) {
        // Start playing
        mp?.start()
        mp?.setLooping(true)
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
     * Get the angle between the given vector and this sound in radians
     */
    fun getAngleFrom(vector: FloatArray): Float {
        // Calculate the dot product between the 2 vectors
        val dot: Float = vector[0]*directionVector[0] + vector[1]*directionVector[1] + vector[2]*directionVector[2]

        // Calculate the product of the magnitudes of the 2 vectors
        val absProduct: Float = sqrt(vector[0].pow(2) + vector[1].pow(2) + vector[2].pow(2)) * sqrt(directionVector[0].pow(2) + directionVector[1].pow(2) + directionVector[2].pow(2))

        // Angle between the vectors
        return acos(dot/absProduct)
    }

    /**
     * Return whether media player exists
     */
    fun isMediaPlayerNull(): Boolean = (mediaPlayer == null)
}