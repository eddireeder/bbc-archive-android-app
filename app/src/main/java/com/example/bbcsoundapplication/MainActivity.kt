package com.example.bbcsoundapplication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.hardware.SensorManager
import android.widget.TextView
import kotlin.math.PI

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var rotationVectorSensor: Sensor
    private lateinit var testSound: Sound
    private val primaryAngle: Float = 5.0f
    private val secondaryAngle: Float = 30.0f

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

        // Initialise test sound
        testSound = Sound(
            "http://bbcsfx.acropolis.org.uk/assets/07076051.wav",
            floatArrayOf(1.0f, 0.0f, 0.0f)
        )
    }

    override fun onResume() {
        super.onResume()

        rotationVectorSensor?.also { sensor ->
            // Register listener (this class) with sensor manager
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()

        // Unregister this listener
        sensorManager.unregisterListener(this)
    }

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

        // Get the angle between the device aim and the sound
        val angleFromSound = testSound.getAngleFrom(aimVector)

        // Convert the angle to degrees
        val angleFromSoundDegrees: Float = angleFromSound*(180.0f/ PI.toFloat())

        // Display the angle
        val textView3 = findViewById<TextView>(R.id.textView3).apply {
            text = angleFromSoundDegrees.toString()
        }

        // Execute sound logic
        if (angleFromSoundDegrees <= secondaryAngle) {
            if (testSound.isMediaPlayerNull()) {
                testSound.startStreaming()
            }
            // Set sound volume relative to distance from sound
            testSound.setVolume(1.0f - (angleFromSoundDegrees/secondaryAngle))
        } else {
            if (!testSound.isMediaPlayerNull()) {
                testSound.stopPlaying()
            }
        }
    }
}
