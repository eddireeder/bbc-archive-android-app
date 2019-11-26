package com.example.thesonosynthesiserapplication

import android.content.Context
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
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
     * Function to get an array of sounds from the server
     */
    suspend fun fetchSounds(): MutableList<Sound>? = suspendCoroutine { continuation ->

        // Instantiate the RequestQueue
        val queue = Volley.newRequestQueue(context)

        // Retrieve selected sound JSON from server
        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, "${context.resources.getString(R.string.api_url)}/sounds", null,
            Response.Listener {response ->

                // Initialise mutable list to return
                val sounds: MutableList<Sound> = mutableListOf()

                // Extract the JSON array of sounds
                val soundJSONArray: JSONArray = response.getJSONArray("sounds")

                // Loop through sounds
                for (i in 1..soundJSONArray.length()) {

                    // Extract the sound JSON
                    val soundJSON: JSONObject = soundJSONArray.getJSONObject(i - 1)

                    val sound: Sound = Sound(
                        soundJSON.getInt("id"),
                        if (soundJSON.isNull("directionX")) null else soundJSON.getDouble("directionX").toFloat(),
                        if (soundJSON.isNull("directionY")) null else soundJSON.getDouble("directionY").toFloat(),
                        if (soundJSON.isNull("directionZ")) null else soundJSON.getDouble("directionZ").toFloat(),
                        soundJSON.getString("location"),
                        soundJSON.getString("description"),
                        soundJSON.getString("category"),
                        soundJSON.getString("cdNumber"),
                        soundJSON.getString("cdName"),
                        soundJSON.getInt("trackNumber"),
                        soundJSON.getBoolean("selected"),
                        soundJSON.getBoolean("onPhone")
                    )

                    // Create sound target object and add to list
                    sounds.add(sound)
                }

                // Resume suspended function with result
                continuation.resume(sounds)
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