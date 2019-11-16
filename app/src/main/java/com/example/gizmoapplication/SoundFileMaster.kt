package com.example.gizmoapplication

import android.content.Context
import android.util.Log
import android.widget.TextView
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SoundFileMaster(val context: Context) {

    private val mainActivity: MainActivity = context as MainActivity
    private val baseUrl: String = "http://bbcsfx.acropolis.org.uk/assets"

    /**
     * Method updates sound files in storage to match the given list of sounds
     */
    suspend fun updateSoundFilesToMatch(sounds: MutableList<Sound>) {

        // Retrieve external storage directory
        val dir: File? = context.getExternalFilesDir(null)

        // Retrieve all files inside directory
        val files: Array<File>? = dir?.listFiles()

        // Log error if can't retrieve files
        if (files == null) println("Can't retrieve files from local storage")

        // Iterate through files in directory
        files?.let {

            for (file in files) {

                listFilesInExternalStorage()

                // Check whether file exists in new list (sound targets)
                var existsInSoundTargets = false

                for (sound in sounds) {

                    // If file's name matches
                    if (file.name == "sound_${sound.location}") {

                        println("${file.name} is selected, not deleting")

                        existsInSoundTargets = true
                        break
                    }
                }

                // If doesn't exist delete the file
                if (!existsInSoundTargets) {
                    println("${file.name} is not selected, deleting")
                    file.delete()
                }
            }

            // Download any sounds that don't exist in internal storage directory
            for (sound in sounds) {

                listFilesInExternalStorage()

                var existsInStorage = false
                for (file in files) {
                    if (file.name == "sound_${sound.location}") {
                        existsInStorage = true
                        break
                    }
                }

                if (!existsInStorage) {

                    println("${sound.location} doesn't exist, downloading")

                    // Update UI
                    withContext(Dispatchers.Main) {
                        mainActivity.textView.text = "Downloading ${sound.location}"
                    }

                    val success: Boolean = downloadAndSaveFile(
                        "${baseUrl}/${sound.location}",
                        "sound_${sound.location}"
                    )
                }
            }

            // Update UI
            withContext(Dispatchers.Main) {
                mainActivity.textView.text = ""
            }
        }
    }

    /**
     * Method to download a file and save it in a given location
     */
    suspend fun downloadAndSaveFile(downloadUrl: String, fileName: String): Boolean = suspendCoroutine { continuation ->

        // Create a path to save the file on external storage
        val file: File = File(context.getExternalFilesDir(null), fileName)

        // Download file
        Fuel.download(downloadUrl)
            .fileDestination { response, request -> file }
            .responseString { result ->
                when (result) {
                    is Result.Success -> {
                        println("file downloaded")
                        continuation.resume(true)
                    }
                    is Result.Failure -> {
                        println("file not downloaded")
                        continuation.resume(false)
                    }
                }
            }
    }

    fun listFilesInExternalStorage() {
        val dir: File? = context.getExternalFilesDir(null)
        val files: Array<File>? = dir?.listFiles()
        files?.let {
            var string = ""
            for (file in files) string += "${file.name} "
            println(string)
        }
    }
}