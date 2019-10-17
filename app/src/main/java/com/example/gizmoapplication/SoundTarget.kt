package com.example.gizmoapplication

import android.media.MediaPlayer
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

class SoundTarget (
    val directionVector: FloatArray,
    val location: String,
    val description: String,
    val category: String,
    val cdNumber: String,
    val cdName: String,
    val trackNumber: Int,
    val resID: Int
) {

    var degreesFromAim: Float = 180f
    var mediaPlayerWithState: MediaPlayerWithState? = null

    /**
     * Update the current angle from the aim direction in degrees
     */
    fun updateDegreesFromAim(aimVector: FloatArray) {
        // Update variable
        degreesFromAim = getDegreesFrom(aimVector)
    }

    /**
     * Get the angle between the given vector and this sound in degrees
     */
    fun getDegreesFrom(vector: FloatArray): Float {
        // Calculate the dot product between the 2 vectors
        val dot: Float = vector[0]*directionVector[0] + vector[1]*directionVector[1] + vector[2]*directionVector[2]

        // Calculate the product of the magnitudes of the 2 vectors
        val absProduct: Float = sqrt(vector[0].pow(2) + vector[1].pow(2) + vector[2].pow(2)) * sqrt(directionVector[0].pow(2) + directionVector[1].pow(2) + directionVector[2].pow(2))

        // Angle between the vectors
        return acos(dot/absProduct)*(180.0f/ PI.toFloat())
    }
}