package com.example.gizmoapplication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.appcompat.app.AppCompatActivity
import android.hardware.SensorManager
import android.os.*
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.*
import java.lang.Runnable
import kotlin.coroutines.CoroutineContext
import kotlin.math.absoluteValue

class MainActivity : AppCompatActivity(), SensorEventListener {

    lateinit var configuration: Configuration
    lateinit var soundTargetManager: SoundTargetManager

    private var readyToStart: Boolean = false
    val debugMode: Boolean = true

    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var lastSignificantSensorValues: FloatArray? = null
    private val maximumIdleSensorDifference: Float = 0.01f
    private val maxIdleSeconds: Float = 30f
    private lateinit var idleRunnable: Runnable
    private val pausePlayTransitionSeconds: Float = 2f

    private val maxMediaPlayers: Int = 4
    private lateinit var backgroundEffect: BackgroundEffect

    var focusTarget: SoundTarget? = null
    var isFocussed: Boolean = false
    private lateinit var vibrator: Vibrator
    private val uiHandler: Handler = Handler()
    private lateinit var focusTimerRunnable: Runnable
    private var characterIndicesToDisplay: MutableList<Int> = mutableListOf()
    private var focusCharacterDelay: Float = 0f

    private lateinit var particleView: ParticleView
    private lateinit var sensorData: TextView
    private lateinit var anglesToSounds: TextView
    private lateinit var textView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var categoryTextView: TextView
    private lateinit var trackInfoTextView: TextView
    private lateinit var descriptionText: String
    private lateinit var categoryText: String
    private lateinit var trackInfoText: String
    private lateinit var metaSpannable: SpannableString

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

        // Retrieve views
        retrieveViews()

        // Initialise sensor manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Attempt to retrieve rotation vector sensor
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationVectorSensor == null) {
            // Fallback on geomagnetic rotation vector sensor
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

            if (rotationVectorSensor == null) {
                // Update UI
                textView.text = resources.getString(R.string.sensor_unavailable)
            }
        }

        rotationVectorSensor?.also { sensor ->
            // Register listener (this class) with sensor manager
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Initialise background sound (will start to play silently)
        backgroundEffect = BackgroundEffect(this)

        // Initialise vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Initialise focus timer
        focusTimerRunnable = Runnable {
            focusTimerUpdate()
        }

        // Initialise idle runnable timer
        idleRunnable = Runnable {
            onIdle()
        }

        // Launch a new coroutine and keep a reference to its job
        val job = GlobalScope.launch {

            val serverMaster = ServerMaster(this@MainActivity)

            val fetchConfiguration = async { serverMaster.fetchConfiguration() }
            val fetchSoundTargets = async { serverMaster.fetchSoundTargets() }

            val configuration = fetchConfiguration.await()
            val soundTargets = fetchSoundTargets.await()

            if (configuration != null && soundTargets != null) {

                // Assign to main instance
                this@MainActivity.configuration = configuration

                // Assign to main instance
                this@MainActivity.soundTargetManager = SoundTargetManager(
                    this@MainActivity,
                    soundTargets,
                    maxMediaPlayers,
                    configuration.secondaryAngle
                )

                // Set as ready to start
                readyToStart = true

                // Update UI
                textView.text = resources.getString(R.string.start_message)
            }

            else {

                // Update UI
                textView.text = resources.getString(R.string.connection_error)
            }
        }
    }

    /**
     * Called during view initialisation
     */
    fun retrieveViews() {

        particleView = findViewById(R.id.particleView)
        sensorData = findViewById(R.id.sensorData)
        anglesToSounds = findViewById(R.id.anglesToSounds)
        descriptionTextView = findViewById(R.id.description)
        categoryTextView = findViewById(R.id.category)
        trackInfoTextView = findViewById(R.id.trackInfo)
        textView = findViewById(R.id.textView)
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

        // Display sensor data if debug on
        if (debugMode) sensorData.text = "(${event.values[0].toString().slice(0..4)}, ${event.values[1].toString().slice(0..4)}, ${event.values[2].toString().slice(0..4)})"

        // Check whether need to pause
        checkWhetherIdle(event.values)

        // If particle view is not set to playing, don't continue
        if (particleView.state != ParticleView.PLAYING) return

        // Calculate the phone's aim vector
        val aimDirectionVector: FloatArray = calculateAimVector(event.values)

        soundTargetManager.apply {

            // Update all sound targets with new degrees from aim
            updateSoundTargetsDegreesFromAim(aimDirectionVector)

            // Order sound targets
            reorderSoundTargets()

            // Reallocate media players
            reallocateMediaPlayers()

            // Update media players volumes
            updateMediaPlayerVolumes()
        }

        // If debug mode then show the latest angles to sounds
        if (debugMode) anglesToSounds.text = soundTargetManager.generateAnglesToSoundsString()

        // Ensure focussing on target if needed, else ensure not focussing
        for (i in soundTargetManager.orderedSoundTargets.indices) {

            // If the closest target
            if (i == 0) {

                // If within the primary angle
                if (soundTargetManager.orderedSoundTargets[i].degreesFromAim <= configuration.primaryAngle) {

                    // Ensure focussing on target
                    if (focusTarget == null) startFocussing(soundTargetManager.orderedSoundTargets[i])
                }
            }

            // Check whether need to stop focussing
            if (
                soundTargetManager.orderedSoundTargets[i].degreesFromAim > configuration.primaryAngle &&
                focusTarget == soundTargetManager.orderedSoundTargets[i]
            ) {
                stopFocussing()
            }
        }

        // Set the background volume
        backgroundEffect.setVolume(calculateBackgroundVolume())
    }

    /**
     * Handle touch events
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {

        // If the action is a press down
        if (event.action == MotionEvent.ACTION_DOWN) {

            // Play the experience
            play()
        }
        return true
    }

    /**
     * Check whether the phone is being used, if not then pause the experience
     */
    fun checkWhetherIdle(sensorValues: FloatArray) {

        // If the particle view is not playing, do nothing
        if (particleView.state != ParticleView.PLAYING) return

        // If we don't have any significant sensor values yet
        if (lastSignificantSensorValues == null) {

            // Record values
            lastSignificantSensorValues = sensorValues.copyOf()

            // Set new runnable timer
            uiHandler.postDelayed(idleRunnable, (maxIdleSeconds*1000f).toLong())

            return
        }

        lastSignificantSensorValues?.let {
            for (i in 0..2) {

                // Check whether there is a significant difference from the last recorded values
                if ((it[i] - sensorValues[i]).absoluteValue > maximumIdleSensorDifference) {

                    // Cancel the existing runnable
                    uiHandler.removeCallbacks(idleRunnable)

                    // Reset significant values
                    lastSignificantSensorValues = sensorValues.copyOf()

                    // Set new runnable
                    uiHandler.postDelayed(idleRunnable, (maxIdleSeconds*1000f).toLong())

                    return
                }
            }
        }
    }

    /**
     * Function run by idle runnable when idle
     */
    fun onIdle() {

        // Reset the last sensor values
        lastSignificantSensorValues = null

        // Pause experience
        pause()
    }

    /**
     * Attempts to pause the whole experience
     */
    fun pause() {

        // Don't continue if particle view is not playing
        if (particleView.state != 3) return

        // Pause the particle view
        particleView.pause()

        // Set the start message after a delay
        uiHandler.postDelayed(Runnable {
            textView.text = resources.getString(R.string.start_message)
        }, (pausePlayTransitionSeconds*1000).toLong())

        // Stop focussing if needed
        if (focusTarget != null) {
            stopFocussing()
        }

        // Set the volume of background effect to 0
        backgroundEffect.setVolume(0f)

        // Set the volume of each media player in pool to 0
        soundTargetManager.setVolumeForAllSoundTargets(0f)
    }

    /**
     * Attempts to play the whole experience
     */
    fun play() {

        // If the particle view is paused and we're ready to start
        if (particleView.state == 2 && readyToStart) {

            // Start the particle view
            particleView.play()

            // Remove the start message
            textView.text = ""
        }
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
     * The logic for controlling the background volume
     */
    fun calculateBackgroundVolume(): Float {

        // Volume should be 0 if focussed on a sound
        if (isFocussed) return 0f

        // If there are no sound targets return full volume
        if (soundTargetManager.orderedSoundTargets.size == 0) return 1f

        // Retrieve the minimum angle from the closest sound
        val minAngleFromSound: Float = soundTargetManager.orderedSoundTargets[0].degreesFromAim

        // Volume relative to distance to sound centre
        if (minAngleFromSound < configuration.secondaryAngle) return 0.2f + 0.8f*(minAngleFromSound/configuration.secondaryAngle)

        // Volume should be full as we're not near any sounds
        return 1f
    }

    /**
     * Start focussing on the given sound
     */
    fun startFocussing(soundTarget: SoundTarget) {

        focusTarget = soundTarget

        // Initialise spannable strings
        descriptionText = soundTarget.description
        categoryText = soundTarget.category
        trackInfoText = "${soundTarget.cdNumber} ${soundTarget.cdName} - ${soundTarget.trackNumber}"

        metaSpannable = SpannableString(descriptionText + categoryText + trackInfoText)

        // Generate a list of indices to display
        characterIndicesToDisplay = (metaSpannable.indices).toMutableList()

        // Calculate the delay between each character
        focusCharacterDelay = configuration.timeToFocus/characterIndicesToDisplay.size

        // Start displaying characters
        uiHandler.postDelayed(focusTimerRunnable, 0)
    }

    /**
     * Stop focussing on any sounds
     */
    fun stopFocussing() {

        isFocussed = false
        focusTarget = null

        // Update character indices to display as empty
        characterIndicesToDisplay.clear()

        // Prevent any callbacks that may exist
        uiHandler.removeCallbacks(focusTimerRunnable)

        // Update text views
        descriptionTextView.text = ""
        categoryTextView.text = ""
        trackInfoTextView.text = ""
    }

    /**
     * The update logic that shows one character of meta data
     */
    fun focusTimerUpdate() {

        // Select a random character to show
        val characterIndex: Int = characterIndicesToDisplay.removeAt((characterIndicesToDisplay.indices).random())

        // Set the character to display in the spannable
        metaSpannable.setSpan(
            ForegroundColorSpan(resources.getColor(R.color.colorText, null)),
            characterIndex, (characterIndex + 1),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Update text views
        descriptionTextView.text = metaSpannable.subSequence(
            0,
            descriptionText.length
        )
        categoryTextView.text = metaSpannable.subSequence(
            descriptionText.length,
            descriptionText.length + categoryText.length
        )
        trackInfoTextView.text = metaSpannable.subSequence(
            descriptionText.length + categoryText.length,
            metaSpannable.lastIndex
        )

        // If there are still characters to display
        if (characterIndicesToDisplay.size > 0) {

            // Set runnable to rerun after delay
            uiHandler.postDelayed(focusTimerRunnable, (focusCharacterDelay * 1000f).toLong())

        } else {

            // Finished focussing
            onFocussed()
        }
    }

    /**
     * Called once a sound has been focused on
     */
    fun onFocussed() {

        isFocussed = true

        // Vibrate the phone
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
