package com.example.thesonosynthesiserapplication

import android.content.Context
import android.util.Log
import java.io.File
import kotlin.math.pow

class SoundTargetMaster(
    context: Context,
    soundTargets: MutableList<SoundTarget>,
    private val maxMediaPlayers: Int,
    private val secondaryAngle: Float
) {

    private val context = context as MainActivity
    val orderedSoundTargets: MutableList<SoundTarget> = soundTargets
    private val mediaPlayerPool = MediaPlayerPool(maxMediaPlayers)

    /**
     * Calculate each sound's degrees from aim value and update them
     */
    fun updateSoundTargetsDegreesFromAim(aimDirectionVector: FloatArray) {

        for (soundTarget in orderedSoundTargets) soundTarget.updateDegreesFromAim(aimDirectionVector)
    }

    /**
     * Reorder the list of sound targets based on their degrees from aim value
     */
    fun reorderSoundTargets() {

        orderedSoundTargets.sortBy {it.degreesFromAim}
    }

    /**
     * Return a string used for debugging that shows the angle from each sound
     */
    fun generateAnglesToSoundsString(): String {

        // Initialise empty string
        var anglesToSoundsString = ""

        // Put degrees from sound into debug string
        for (soundTarget in orderedSoundTargets) anglesToSoundsString += " ${soundTarget.degreesFromAim.toInt()}"

        return anglesToSoundsString
    }

    /**
     * Reallocate the media players between the sound targets based on their current order
     */
    fun reallocateMediaPlayers() {

        // Recycle media players first before allocating them
        for (i in orderedSoundTargets.indices) {

            if (i + 1 > maxMediaPlayers || orderedSoundTargets[i].degreesFromAim > secondaryAngle) {

                // Recycle media player if not null and the player has been prepared
                orderedSoundTargets[i].mediaPlayerWithState?.let {

                    if (it.prepared) {
                        mediaPlayerPool.recyclePlayer(it)
                        orderedSoundTargets[i].mediaPlayerWithState = null
                    }
                }
            }
        }

        // Allocate media players to targets that need them (and don't have them)
        for (i in orderedSoundTargets.indices) {

            if (
                orderedSoundTargets[i].mediaPlayerWithState === null &&
                i + 1 <= maxMediaPlayers &&
                orderedSoundTargets[i].degreesFromAim <= secondaryAngle
            ) {

                // If media player is available in the pool
                mediaPlayerPool.requestPlayer()?.let { mediaPlayerWithState ->

                    // Assign to sound target
                    orderedSoundTargets[i].mediaPlayerWithState = mediaPlayerWithState

                    // Start playing sound resource
                    /*
                    context.resources.openRawResourceFd(orderedSoundTargets[i].resID)
                        ?.let { assetFileDescriptor ->
                            mediaPlayerWithState.mediaPlayer.run {
                                setDataSource(assetFileDescriptor)
                                prepareAsync()
                            }
                        }

                     */
                    val file: File = File(context.getExternalFilesDir(null), "sound_${orderedSoundTargets[i].location}")
                    mediaPlayerWithState.mediaPlayer.run {
                        setDataSource(file.toString())
                        prepareAsync()
                    }
                }
            }
        }
    }

    /**
     * Set the volumes of the media players depending on their sound target's degrees from aim value
     */
    fun updateMediaPlayerVolumes() {

        for (soundTarget in orderedSoundTargets) {

            // If sound target has media player
            soundTarget.mediaPlayerWithState?.mediaPlayer?.apply {

                // Calculate the new volume
                val volume: Float = if (context.isFocussed) {
                    if (context.focusTarget == soundTarget) {
                        // Focussed on sound -> 100%
                        1f
                    } else {
                        // Focussed on another sound -> 0%
                        0f
                    }
                } else {
                    // Volume relative to distance away (up to 80%)
                    //(0.8f - 0.8f * (soundTarget.degreesFromAim/secondaryAngle))

                    // Volume from 0 -> 100% modelled by y=2^(4(x-1))
                    val x: Float = 1f - (soundTarget.degreesFromAim/secondaryAngle)
                    2f.pow(4f*(x - 1f))
                }

                Log.d("Sound volume", volume.toString())

                // Set the media player volume
                setVolume(volume, volume)
            }
        }
    }

    /**
     * Sets the volume on every media player assigned to a sound target
     */
    fun setVolumeForAllSoundTargets(volume: Float) {

        // For each sound target that has a media player
        for (soundTarget in orderedSoundTargets) {
            soundTarget.mediaPlayerWithState?.let {

                // Set media player volume
                it.mediaPlayer.setVolume(volume, volume)
            }
        }
    }
}