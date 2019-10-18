package com.example.gizmoapplication

import android.content.Context
import android.media.SoundPool

class BackgroundEffect (val context: Context) {

    private val soundPool: SoundPool
    private var streamID: Int? = null

    /**
     * Initialise the sound pool and start playing the effect
     */
    init {
        SoundPool.Builder().let {
            soundPool = it.build()
        }
        soundPool.setOnLoadCompleteListener(object: SoundPool.OnLoadCompleteListener {
            override fun onLoadComplete(soundPool: SoundPool, sampleId: Int, status: Int) {
                streamID = soundPool.play(sampleId, 1.0f, 1.0f, 1, -1, 1.0f)
            }
        })
        soundPool.load(context, R.raw.soundbed, 1)
    }

    /**
     *  Pause the sound effect
     */
    fun pause() {
        streamID?.let {
            soundPool.pause(it)
        }
    }

    /**
     * Set the sound volume
     */
    fun setVolume(volume: Float) {
        streamID?.let {
            soundPool.setVolume(it, volume, volume)
        }
    }

    /**
     * Resume the sound effect
     */
    fun resume() {
        streamID?.let {
            soundPool.resume(it)
        }
    }
}