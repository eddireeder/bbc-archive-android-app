package com.example.bbcsoundapplication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.hardware.SensorManager
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var rotationVectorSensor: Sensor

    private val soundTargets: MutableList<SoundTarget> = mutableListOf()
    private val primaryAngle: Float = 5.0f
    private val secondaryAngle: Float = 15.0f
    private lateinit var staticEffect: StaticEffect

    private var targettedSound: SoundTarget? = null
    private var isFocussed: Boolean = false
    private lateinit var focusTimer: CountDownTimer
    private lateinit var vibrator: Vibrator

    private var currentBearing: Float = 0.0f

    /**
     * Called on creation of Activity
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        generateTestSoundTargets(20, 30.0f)

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

        // Stop the static effect
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
    }

    /**
     * Called on change of sensor data
     */
    override fun onSensorChanged(event: SensorEvent) {
        // Retrieve value
        val rotationVector: FloatArray = event.values

        // Calculate rotation matrix
        var rotationMatrix: FloatArray = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)

        // Apply rotation matrix to device Y vector (0, 1, 0) to get the aim direction
        val aimVector: FloatArray = floatArrayOf(rotationMatrix[1], rotationMatrix[4], rotationMatrix[7])

        // Store the minimum angle from a sound
        var minAngleFromSound: Float = secondaryAngle

        // Record the sounds in earshot to display in logs
        val inEarshot: MutableList<SoundTarget> = mutableListOf()

        for (soundTarget in soundTargets) {

            // Get the angle between the device aim and the sound in degrees
            val angleFromSound = soundTarget.getDegreesFrom(aimVector)

            // Replace if new minimum
            if (angleFromSound < minAngleFromSound) minAngleFromSound = angleFromSound

            // If the aim is inside the secondary zone
            if (angleFromSound <= secondaryAngle) {

                // Add to in earshot list
                inEarshot.add(soundTarget)

                // Ensure sound is streaming
                if (soundTarget.isMediaPlayerNull()) soundTarget.startStreaming()

                if (isFocussed && (targettedSound != soundTarget)) {
                    // Focussed on another sound so set the volume to 0
                    soundTarget.setVolume(0.0f)
                } else {
                    // Set the volume relative to distance away
                    soundTarget.setVolume(1.0f - (angleFromSound/secondaryAngle))
                }

                // If the aim is inside the primary zone
                if (angleFromSound <= primaryAngle) {
                    // Start focussing if not already
                    if (targettedSound == null) {
                        startFocussing(soundTarget)
                    }
                } else {
                    // If focussing, stop
                    if (targettedSound == soundTarget) {
                        stopFocussing()
                    }
                }
            } else {
                // Ensure sound has stopped playing
                if (!soundTarget.isMediaPlayerNull()) {
                    soundTarget.stopPlaying()
                }
            }
        }

        // Log number of sounds in earshot
        Log.i("Audible", inEarshot.size.toString())

        // Set static effect volume
        staticEffect.setVolume(0.2f + 0.8f*(minAngleFromSound/secondaryAngle))
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
                text = "${it.CDNumber} ${it.CDName} - ${it.trackNum}"
            }
        }
        staticEffect.pause()
    }

    /**
     * Generate an array of randomly chosen and spaced sound targets
     */
    fun generateTestSoundTargets(numTargets: Int, minDistanceBetween: Float) {
        // Update UI
        val textView = findViewById<TextView>(R.id.textView).apply {
            text = getString(R.string.retrieving_sounds)
        }

        // Instantiate the RequestQueue
        val queue = Volley.newRequestQueue(this)
        val url = "http://bbcsfx.acropolis.org.uk/assets/BBCSoundEffects.csv"

        // Request a string response from the provided URL.
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> {response ->
                // Update UI
                val textView = findViewById<TextView>(R.id.textView).apply {
                    text = "Placing sounds"
                }

                // Parse response into sound targets
                val responseLines: List<String> = response.split("\n").drop(1)

                // Randomly pick N targets
                val lines: List<String> = responseLines.shuffled().take(numTargets)

                lines.forEachIndexed {index, line ->
                    val lineSplit = line.drop(1).dropLast(1).split("""","""")

                    while (true) {
                        var valid: Boolean = true

                        // Generate a direction vector for the sound (SHOULD BE DONE IN THE SERVER FOR THE REAL SOUNDS)
                        val directionVector: FloatArray = generateRandomUnitVector()

                        // Calculate proximity to all current sounds
                        for (soundTarget in soundTargets) {
                            if (soundTarget.getDegreesFrom(directionVector) <= minDistanceBetween) {
                                valid = false
                                break
                            }
                        }

                        // Add sound target if still valid
                        if (valid) {
                            soundTargets.add(SoundTarget(
                                lineSplit[0],
                                lineSplit[1],
                                lineSplit[2].toInt(),
                                lineSplit[3],
                                lineSplit[4],
                                lineSplit[5],
                                lineSplit[6],
                                directionVector
                            ))
                            break
                        }
                    }
                }
                // Update UI
                textView.apply {
                    text = ""
                }
            },
            Response.ErrorListener {
                // Update UI
                val textView = findViewById<TextView>(R.id.textView).apply {
                    text = getString(R.string.connection_error)
                }
            })

        // Don't cache the request
        stringRequest.setShouldCache(false);

        // Add the request to the RequestQueue.
        queue.add(stringRequest)
    }

    /**
     * Generate a random unit vector
     */
    fun generateRandomUnitVector(): FloatArray {
        // Generate random vector
        val vector: FloatArray = floatArrayOf(
            Math.random().toFloat() - 0.5f,
            Math.random().toFloat() - 0.5f,
            Math.random().toFloat() - 0.5f
        )

        // Normalise vector
        val magnitude: Float = sqrt(vector[0].pow(2) + vector[1].pow(2) + vector[2].pow(2))
        return floatArrayOf(
            vector[0]/magnitude,
            vector[1]/magnitude,
            vector[2]/magnitude
        )
    }

    /**
     * Get an array of sound targets from the server
     */
    fun generateSoundTargets() {

    }
}
