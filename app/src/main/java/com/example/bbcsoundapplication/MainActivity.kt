package com.example.bbcsoundapplication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener, MediaPlayer.OnPreparedListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var rotationVectorSensor: Sensor
    private val targetVector: FloatArray = floatArrayOf(1.0f, 0.0f, 0.0f)
    private lateinit var mediaPlayer: MediaPlayer
    private val primaryAngle: Float = 5.0f

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

        // Initialise media player
        mediaPlayer = MediaPlayer()
        playSound()
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

        // Calculate the angle between the aim direction and the target direction
        val angleInRadians: Float = getAngleBetweenVectors(aimVector, targetVector)

        // Convert to degrees
        val angleInDegrees: Float = angleInRadians*(180.0f/PI.toFloat())

        // Display the angle
        val textView3 = findViewById<TextView>(R.id.textView3).apply {
            text = angleInDegrees.toString()
        }

        if (angleInDegrees < primaryAngle) {
            // Ensure volume is 100%
            mediaPlayer.setVolume(1.0f, 1.0f)
        } else {
            mediaPlayer.setVolume(0.0f, 0.0f)
        }
    }

    fun getAngleBetweenVectors(v1: FloatArray, v2: FloatArray): Float {
        // Calculate the dot product between the 2 vectors
        val dot: Float = v1[0]*v2[0] + v1[1]*v2[1] + v1[2]*v2[2]

        // Calculate the product of the magnitudes of the 2 vectors
        val absProduct: Float = sqrt(v1[0]*v1[0] + v1[1]*v1[1] + v1[2]*v1[2])*sqrt(v2[0]*v2[0] + v2[1]*v2[1] + v2[2]*v2[2])

        // Angle between the vectors
        return acos(dot/absProduct)
    }

    fun playSound() {
        val testSoundURL = "http://bbcsfx.acropolis.org.uk/assets/07076051.wav"
        mediaPlayer.apply {
            setDataSource(testSoundURL)
            setOnPreparedListener(this@MainActivity)
            prepareAsync()
        }
    }

    /** Called when media player is ready */
    override fun onPrepared(mediaPlayer: MediaPlayer) {
        mediaPlayer.start()
    }
}
