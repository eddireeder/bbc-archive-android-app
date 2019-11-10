package com.example.gizmoapplication

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ServerMaster(val context: Context) {

    /**
     * Function to get the configuration parameters from the server
     */
    suspend fun fetchConfiguration(): Configuration? = suspendCoroutine { continuation ->

        // Instantiate the RequestQueue
        val queue = Volley.newRequestQueue(context)

        // Retrieve sound JSON from server
        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, "${context.resources.getString(R.string.api_url)}/configuration", null,
            Response.Listener { response ->

                // If null then return null
                if (response.isNull("configuration")) continuation.resume(null)

                else {

                    // Extract the JSON configuration object
                    val configurationJSON: JSONObject = response.getJSONObject("configuration")

                    // Create configuration object
                    val configuration = Configuration(
                        configurationJSON.getDouble("primaryAngle").toFloat(),
                        configurationJSON.getDouble("secondaryAngle").toFloat(),
                        configurationJSON.getDouble("timeToFocus").toFloat(),
                        configurationJSON.getInt("maxMediaPlayers"),
                        configurationJSON.getDouble("maxIdleSensorDifference").toFloat(),
                        configurationJSON.getDouble("maxIdleSeconds").toFloat()
                    )

                    // Return configuration
                    continuation.resume(configuration)
                }
            },
            Response.ErrorListener {

                // Return empty configuration
                continuation.resume(null)
            }
        )

        // Don't cache the request
        jsonObjectRequest.setShouldCache(false)

        // Add the request to the RequestQueue
        queue.add(jsonObjectRequest)
    }

    /**
     * Function to get an array of sound targets from the server
     */
    suspend fun fetchSoundTargets(): MutableList<SoundTarget>? = suspendCoroutine { continuation ->

        // Instantiate the RequestQueue
        val queue = Volley.newRequestQueue(context)

        // Retrieve selected sound JSON from server
        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, "${context.resources.getString(R.string.api_url)}/sounds/selected", null,
            Response.Listener {response ->

                // Initialise mutable list to return
                val soundTargets: MutableList<SoundTarget> = mutableListOf()

                // Extract the JSON array of sounds
                val soundJSONArray: JSONArray = response.getJSONArray("sounds")

                // Loop through sounds
                for (i in 1..soundJSONArray.length()) {

                    // Extract the sound JSON
                    val soundJSON: JSONObject = soundJSONArray.getJSONObject(i - 1)

                    // Extract the sound direction as a float array
                    val directionVector: FloatArray = floatArrayOf(
                        soundJSON.getDouble("directionX").toFloat(),
                        soundJSON.getDouble("directionY").toFloat(),
                        soundJSON.getDouble("directionZ").toFloat()
                    )

                    // Retrieve the rest of the sound data
                    val location: String = soundJSON.getString("location")
                    val description: String = soundJSON.getString("description")
                    val category: String = soundJSON.getString("category")
                    val cdNumber: String = soundJSON.getString("cdNumber")
                    val cdName: String = soundJSON.getString("cdName")
                    val trackNumber: Int = soundJSON.getInt("trackNumber")

                    // Check resource exists and get id
                    val resID: Int = context.resources.getIdentifier(
                        "sound_${location.split(".")[0]}",
                        "raw",
                        context.packageName
                    )
                    if (resID == 0) continue

                    // Create sound target object and add to list
                    soundTargets.add(SoundTarget(directionVector, location, description, category, cdNumber, cdName, trackNumber, resID))
                }

                // Resume suspended function with result
                continuation.resume(soundTargets)
            },
            Response.ErrorListener {

                // Return null
                continuation.resume(null)
            }
        )

        // Don't cache the request
        jsonObjectRequest.setShouldCache(false)

        // Add the request to the RequestQueue
        queue.add(jsonObjectRequest)
    }
}