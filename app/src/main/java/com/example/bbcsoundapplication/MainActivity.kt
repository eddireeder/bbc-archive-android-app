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
import android.widget.TextView
import kotlin.math.PI

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var rotationVectorSensor: Sensor

    private lateinit var soundTargets: Array<SoundTarget>
    private val primaryAngle: Float = 5.0f
    private val secondaryAngle: Float = 30.0f
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
        setContentView(R.layout.activity_main)

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

        // Initialise sound target array for testing
        soundTargets = arrayOf<SoundTarget>(
            SoundTarget("http://bbcsfx.acropolis.org.uk/assets/07076051.wav", floatArrayOf(1.0f, 0.0f, 0.0f)),
            SoundTarget("http://bbcsfx.acropolis.org.uk/assets/07070175.wav", floatArrayOf(1.0f, 0.5f, 0.0f))
        )

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
    }

    /**
     * Called on pausing of activity
     */
    override fun onPause() {
        super.onPause()

        // Unregister this listener
        sensorManager.unregisterListener(this)
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
        val textView = findViewById<TextView>(R.id.textView2).apply {
            text = accuracyString
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

        // Apply rotation matrix to device Z vector (0, 0, 1) to get the aim direction
        val aimVector: FloatArray = floatArrayOf(rotationMatrix[2], rotationMatrix[5], rotationMatrix[8])

        // Display the aim direction vector
        val textView = findViewById<TextView>(R.id.textView).apply {
            text = aimVector.joinToString()
        }

        // Store the minimum angle from a sound
        var minAngleFromSound: Float = secondaryAngle

        for (soundTarget in soundTargets) {

            // Get the angle between the device aim and the sound in degrees
            val angleFromSound = soundTarget.getAngleFrom(aimVector)
            val angleFromSoundDegrees: Float = angleFromSound*(180.0f/ PI.toFloat())

            // Replace if new minimum
            if (angleFromSoundDegrees < minAngleFromSound) minAngleFromSound = angleFromSoundDegrees

            // If the aim is inside the secondary zone
            if (angleFromSoundDegrees <= secondaryAngle) {

                // Ensure sound is streaming and set the volume relative to distance away
                if (soundTarget.isMediaPlayerNull()) soundTarget.startStreaming()
                soundTarget.setVolume(1.0f - (angleFromSoundDegrees/secondaryAngle))

                // If the aim is inside the primary zone
                if (angleFromSoundDegrees <= primaryAngle) {
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

        // Set static effect volume
        staticEffect.setVolume(0.2f + 0.8f*(minAngleFromSound/secondaryAngle))
    }

    /**
     * Start focussing on the given sound
     */
    fun startFocussing(soundTarget: SoundTarget) {
        targettedSound = soundTarget
        focusTimer.start()
        val textView = findViewById<TextView>(R.id.textView3).apply {
            text = "Focussing"
        }
    }

    /**
     * Stop focussing on any sounds
     */
    fun stopFocussing() {
        isFocussed = false
        targettedSound = null
        focusTimer.cancel()
        val textView = findViewById<TextView>(R.id.textView3).apply {
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
        val textView = findViewById<TextView>(R.id.textView3).apply {
            text = "Focussed"
        }
        staticEffect.pause()
    }
}
