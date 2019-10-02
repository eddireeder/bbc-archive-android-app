package com.example.bbcsoundapplication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.hardware.SensorManager
import android.media.SoundPool
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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var rotationVectorSensor: Sensor

    private val soundTargets: MutableList<SoundTarget> = mutableListOf()
    private val primaryAngle: Float = 5.0f
    private val secondaryAngle: Float = 15.0f
    private lateinit var soundPool: SoundPool
    private lateinit var staticEffect: StaticEffect

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

        // Initialise sound pool
        SoundPool.Builder().let {
            soundPool = it.build()
        }
        soundPool.setOnLoadCompleteListener(object: SoundPool.OnLoadCompleteListener {
            override fun onLoadComplete(soundPool: SoundPool, soundID: Int, status: Int) {
                for (soundTarget in soundTargets) {
                    if (soundTarget.soundID == soundID) {
                        soundTarget.hasLoaded = true
                    }
                }
            }
        })

        // Get sound targets
        generateSoundTargets()

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
        // TODO: Handle too low of an accuracy
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

            // If the sound hasn't loaded then don't continue with calculations
            if (soundTarget.hasLoaded) {

                // Get the angle between the device aim and the sound in degrees
                val angleFromSound = soundTarget.getDegreesFrom(aimVector)

                // Replace if new minimum
                if (angleFromSound < minAngleFromSound) minAngleFromSound = angleFromSound

                // If the aim is inside the secondary zone
                if (angleFromSound <= secondaryAngle) {

                    // Add to in earshot list
                    inEarshot.add(soundTarget)

                    // Calculate volume
                    var volume: Float

                    if (isFocussed) {
                        if (targettedSound == soundTarget) {
                            // Focussed on sound so full volume
                            volume = 1.0f
                        } else {
                            // Focussed on another sound so no volume
                            volume = 0.0f
                        }
                    } else {
                        // Volume relative to distance away (up to 80%)
                        volume = (0.8f - 0.8f * (angleFromSound / secondaryAngle))
                    }

                    // Ensure sound is playing and set the volume
                    if (soundTarget.streamID == null) {
                        soundTarget.streamID =
                            soundPool.play(soundTarget.soundID, volume, volume, 1, -1, 1.0f)
                    } else {
                        soundTarget.streamID?.let {
                            soundPool.setVolume(it, volume, volume)
                        }
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
                    soundTarget.streamID?.let {
                        soundPool.stop(it)
                        soundTarget.streamID = null
                    }
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
    fun generateSoundTargets() {
        // Update UI
        val textView = findViewById<TextView>(R.id.textView).apply {
            text = getString(R.string.retrieving_sounds)
        }

        // TODO: Retrieve list of sounds to use (and their descriptions) from server. The server assumes they will exist in the app.

        // For now hard code it
        val responseJSON = JSONObject("""{"sounds": [{"direction": {"x": 1.0, "y": 1.0, "z": 1.0}, "location": "07041022.wav", "description": "Example description", "category": "Example category", "CDNumber": "CD123", "CDName": "Example CD", "trackNum": 1}]}""")

        // Retrieve list of sounds
        val soundJSONList: JSONArray = responseJSON.getJSONArray("sounds")

        // Loop through sounds
        for (i in 1..soundJSONList.length()) {

            // Extract the sound JSON
            val soundJSON: JSONObject = soundJSONList.getJSONObject(i - 1)

            // Extract the sound direction
            val soundDirectionJSON: JSONObject = soundJSON.getJSONObject("direction")
            val directionVector: FloatArray = floatArrayOf(
                soundDirectionJSON.getDouble("x").toFloat(),
                soundDirectionJSON.getDouble("y").toFloat(),
                soundDirectionJSON.getDouble("z").toFloat()
            )

            // Retrieve the rest of the sound data
            val location: String = soundJSON.getString("location")
            val description: String = soundJSON.getString("description")
            val category: String = soundJSON.getString("category")
            val CDNumber: String = soundJSON.getString("CDNumber")
            val CDName: String = soundJSON.getString("CDName")
            val trackNum: Int = soundJSON.getInt("trackNum")

            // Check resource exists and get id
            val resID: Int = resources.getIdentifier("sound_${location.split(".")[0]}", "raw", this.packageName)
            if (resID == 0) continue

            // Start to load into sound pool
            val soundID: Int = soundPool.load(this, resID, 1)

            // Create sound target object and add to list
            soundTargets.add(SoundTarget(directionVector, location, description, category, CDNumber, CDName, trackNum, soundID))
        }
        // Update UI
        textView.apply {
            text = ""
        }
    }
}
