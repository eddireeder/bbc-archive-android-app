package com.example.bbcsoundapplication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.view.View
import android.widget.TextView

class MainActivity : AppCompatActivity(), SensorEventListener, MediaPlayer.OnPreparedListener {

    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var rotationVector: FloatArray? = null
    private var rotationVectorAccuracy: Int? = null

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
        // Do something if the accuracy changes
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Show new data
        rotationVector = event.values
        rotationVectorAccuracy = event.accuracy

        // Display the data for now
        rotationVector?.also { vector ->
            val textView = findViewById<TextView>(R.id.textView).apply {
                text = vector.joinToString()
            }
        }
    }

    fun playSound(view: View) {
        val testSoundURL = "http://bbcsfx.acropolis.org.uk/assets/07076051.wav"
        val mediaPlayer: MediaPlayer? = MediaPlayer().apply {
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
