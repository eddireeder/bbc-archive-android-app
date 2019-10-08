package com.example.gizmoapplication

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

class SoundTarget (
    val directionVector: FloatArray,
    val location: String,
    val description: String,
    val category: String,
    val CDNumber: String,
    val CDName: String,
    val trackNum: Int,
    val soundID: Int
) {

    var hasLoaded: Boolean = false
    var streamID: Int? = null

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