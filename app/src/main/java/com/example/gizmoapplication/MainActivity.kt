package com.example.gizmoapplication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var rotationVectorSensor: Sensor

    private val soundTargets: MutableList<SoundTarget> = mutableListOf()
    val primaryAngle: Float = 5f
    val secondaryAngle: Float = 30f
    private lateinit var staticEffect: StaticEffect

    private val maxMediaPlayers: Int = 1
    private val mediaPlayerPool = MediaPlayerPool(maxMediaPlayers)

    var minAngleFromSound: Float = 180f
    private var targettedSound: SoundTarget? = null
    private var isFocussed: Boolean = false
    private lateinit var focusTimer: CountDownTimer
    private lateinit var vibrator: Vibrator

    /**
     * Called on creation of Activity
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_main)

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialise sensor manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Attempt to retrieve rotation vector sensor
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationVectorSensor == null) {
            // Fallback on geomagnetic rotation vector sensor
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

            if (rotationVectorSensor == null) {
                // Handle unavailable sensor
            }
        }

        // Get sound targets
        updateSoundTargets("http://ec2-3-8-216-213.eu-west-2.compute.amazonaws.com/api/sounds")

        // Initialise static background sound and start playing
        staticEffect = StaticEffect(this)

        // Initialise the focus timer
        focusTimer = object: CountDownTimer(5000, 5000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() = onFocussed()
        }

        // Initialise vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Called on resuming of activity
     */
    override fun onResume() {
        super.onResume()

        rotationVectorSensor?.also { sensor ->
            // Register listener (this class) with sensor manager
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Resume the static effect
        staticEffect.resume()
    }

    /**
     * Called on pausing of activity
     */
    override fun onPause() {
        super.onPause()

        // Unregister this listener
        sensorManager.unregisterListener(this)

        // Pause the static effect
        staticEffect.pause()
    }

    /**
     * Called on change of sensor accuracy
     */
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Show accuracy
        var accuracyString: String = when (accuracy) {
            SensorManager.SENSOR_STATUS_NO_CONTACT -> "No contact"
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Accuracy low"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Accuracy medium"
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Accuracy high"
            else -> ""
        }
        // TODO: Handle too low of an accuracy
    }

    /**
     * Called on change of sensor data
     */
    override fun onSensorChanged(event: SensorEvent) {
        // Calculate the phone's aim vector
        val aimVector: FloatArray = calculateAimVector(event.values)

        // Update all sound targets with new degrees from aim
        for (soundTarget in soundTargets) soundTarget.updateDegreesFromAim(aimVector)

        // Retrieve ordered list of sound targets
        val orderedSoundTargets: List<SoundTarget> = soundTargets.sortedBy {it.degreesFromAim}

        // Recycle media players first before allocating them
        for (i in orderedSoundTargets.indices) {
            if (
                i + 1 > maxMediaPlayers ||
                orderedSoundTargets[i].degreesFromAim > secondaryAngle
            ) {
                // Recycle media player if not null and the player has been prepared
                orderedSoundTargets[i].mediaPlayerWithState?.let {
                    if (it.prepared) {
                        mediaPlayerPool.recyclePlayer(it)
                        orderedSoundTargets[i].mediaPlayerWithState = null
                    }
                }
            }
        }

        for (i in orderedSoundTargets.indices) {

            // If the closest target
            if (i === 0) {
                // Assign min angle
                minAngleFromSound = orderedSoundTargets[i].degreesFromAim

                if (orderedSoundTargets[i].degreesFromAim <= primaryAngle) {
                    // Ensure focussing on target
                    if (targettedSound != orderedSoundTargets[i]) {
                        startFocussing(orderedSoundTargets[i])
                    }
                } else {
                    // If focussing, stop
                    if (targettedSound == orderedSoundTargets[i]) {
                        stopFocussing()
                    }
                }
            }

            // Find targets that have no media player but need one
            if (
                orderedSoundTargets[i].mediaPlayerWithState === null &&
                i + 1 <= maxMediaPlayers &&
                orderedSoundTargets[i].degreesFromAim <= secondaryAngle
            ) {
                // If media player is available in the pool
                mediaPlayerPool.requestPlayer()?.let {mediaPlayerWithState ->

                    // Assign to sound target
                    orderedSoundTargets[i].mediaPlayerWithState = mediaPlayerWithState

                    // Start playing sound resource
                    resources.openRawResourceFd(orderedSoundTargets[i].resID)?.let { assetFileDescriptor ->
                        mediaPlayerWithState.mediaPlayer.run {
                            setDataSource(assetFileDescriptor)
                            prepareAsync()
                        }
                    }
                }
            }

            // If sound target has media player
            orderedSoundTargets[i].mediaPlayerWithState?.mediaPlayer?.apply {

                // Calculate the new volume
                val volume: Float = if (isFocussed) {
                    if (targettedSound == orderedSoundTargets[i]) {
                        // Focussed on sound so full volume
                        1f
                    } else {
                        // Focussed on another sound so 0 volume
                        0f
                    }
                } else {
                    // Volume relative to distance away (up to 80%)
                    (0.8f - 0.8f * (orderedSoundTargets[i].degreesFromAim/secondaryAngle))
                }

                // Set the media player volume
                setVolume(volume, volume)
            }
        }

        // Set static effect volume
        val staticVolume: Float = if (minAngleFromSound < secondaryAngle) {
            // Static volume relative to min angle (only down to 20%)
            0.2f + 0.8f*(minAngleFromSound/secondaryAngle)
        } else {
            1f
        }
        staticEffect.setVolume(staticVolume)
    }

    /**
     * Compute the phone's aim direction vector in world coordinates
     */
    fun calculateAimVector(rotationVector: FloatArray): FloatArray {
        // Calculate rotation matrix
        var rotationMatrix: FloatArray = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)

        // Apply rotation matrix to device Y vector (0, 1, 0) to get the aim direction
        return floatArrayOf(rotationMatrix[1], rotationMatrix[4], rotationMatrix[7])
    }

    /**
     * Start focussing on the given sound
     */
    fun startFocussing(soundTarget: SoundTarget) {
        targettedSound = soundTarget
        focusTimer.start()
        val textView = findViewById<TextView>(R.id.textView).apply {
            text = getString(R.string.focussing)
        }
    }

    /**
     * Stop focussing on any sounds
     */
    fun stopFocussing() {
        isFocussed = false
        targettedSound = null
        focusTimer.cancel()
        val textView = findViewById<TextView>(R.id.textView).apply {
            text = ""
        }
        val descriptionTextView = findViewById<TextView>(R.id.description).apply {
            text = ""
        }
        val categoryTextView = findViewById<TextView>(R.id.category).apply {
            text = ""
        }
        val trackInfoTextView = findViewById<TextView>(R.id.trackInfo).apply {
            text = ""
        }
        staticEffect.resume()
    }

    /**
     * Called once a sound has been focused on
     */
    fun onFocussed() {
        isFocussed = true
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        val textView = findViewById<TextView>(R.id.textView).apply {
            text = getString(R.string.focussed)
        }
        targettedSound?.let {
            val descriptionTextView = findViewById<TextView>(R.id.description).apply {
                text = it.description
            }
            val categoryTextView = findViewById<TextView>(R.id.category).apply {
                text = it.category
            }
            val trackInfoTextView = findViewById<TextView>(R.id.trackInfo).apply {
                text = "${it.cdNumber} ${it.cdName} - ${it.trackNumber}"
            }
        }
        staticEffect.pause()
    }

    /**
     * Get an array of selected sound targets from the server and update soundTargets variable
     */
    fun updateSoundTargets(url: String) {
        // Update UI
        val textView = findViewById<TextView>(R.id.textView).apply {
            text = getString(R.string.retrieving_sounds)
        }

        // Instantiate the RequestQueue
        val queue = Volley.newRequestQueue(this)

        // Retrieve sound JSON from server
        val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            Response.Listener {response ->
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
                    val resID: Int = resources.getIdentifier("sound_${location.split(".")[0]}", "raw", this.packageName)
                    if (resID == 0) continue

                    // Create sound target object and add to list
                    soundTargets.add(SoundTarget(directionVector, location, description, category, cdNumber, cdName, trackNumber, resID))
                }

                // Update UI
                textView.apply {
                    text = ""
                }
            },
            Response.ErrorListener {error ->
                // Update UI
                textView.apply {
                    text = resources.getString(R.string.connection_error)
                }
            }
        )

        // Don't cache the request
        jsonObjectRequest.setShouldCache(false);

        // Add the request to the RequestQueue
        queue.add(jsonObjectRequest)
    }
}
