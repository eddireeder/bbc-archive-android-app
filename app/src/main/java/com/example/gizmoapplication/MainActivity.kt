package com.example.gizmoapplication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.appcompat.app.AppCompatActivity
import android.hardware.SensorManager
import android.os.*
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import androidx.core.os.postDelayed
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Text
import kotlin.math.absoluteValue

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var readyToStart: Boolean = false

    private lateinit var sensorManager: SensorManager
    private lateinit var rotationVectorSensor: Sensor
    private var lastSignificantSensorValues: FloatArray? = null
    private val maximumIdleSensorDifference: Float = 0.05f
    private val maxIdleSeconds: Float = 10f
    private lateinit var idleRunnable: Runnable
    private val pausePlayTransitionSeconds: Float = 2f

    private val soundTargets: MutableList<SoundTarget> = mutableListOf()
    val primaryAngle: Float = 10f
    val secondaryAngle: Float = 50f
    private val maxMediaPlayers: Int = 4
    private lateinit var mediaPlayerPool: MediaPlayerPool
    private lateinit var backgroundEffect: BackgroundEffect
    var minAngleFromSound: Float = 180f

    private val timeToFocus: Float = 3f
    private var focusTarget: SoundTarget? = null
    private var isFocussed: Boolean = false
    private lateinit var vibrator: Vibrator
    private val uiHandler: Handler = Handler()
    private lateinit var focusTimerRunnable: Runnable
    private var characterIndicesToDisplay: MutableList<Int> = mutableListOf()
    private var focusCharacterDelay: Float = 0f

    private lateinit var particleView: ParticleView
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

        // Initialise sensor manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Attempt to retrieve rotation vector sensor
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationVectorSensor == null) {
            // Fallback on geomagnetic rotation vector sensor
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

            if (rotationVectorSensor == null) {
                // TODO: Handle unavailable sensor
            }
        }

        // Initialise media player pool
        mediaPlayerPool = MediaPlayerPool(maxMediaPlayers)

        // Initialise background sound
        backgroundEffect = BackgroundEffect(this)

        // Initialise vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Retrieve views
        particleView = findViewById<ParticleView>(R.id.particleView)
        descriptionTextView = findViewById<TextView>(R.id.description)
        categoryTextView = findViewById<TextView>(R.id.category)
        trackInfoTextView = findViewById<TextView>(R.id.trackInfo)
        textView = findViewById<TextView>(R.id.textView)

        // Initialise focus timer
        focusTimerRunnable = Runnable {
            focusTimerUpdate()
        }

        // Initialise idle runnable timer
        idleRunnable = Runnable {
            onIdle()
        }

        // Get sound targets
        updateSoundTargets("http://ec2-3-8-216-213.eu-west-2.compute.amazonaws.com/api/sounds")
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

        // Start the background effect playing silently
        backgroundEffect.startSilently()
    }

    /**
     * Called on pausing of activity
     */
    override fun onPause() {

        super.onPause()

        // Unregister this listener
        sensorManager.unregisterListener(this)

        // Stop the background effect
        backgroundEffect.stop()

        // Recycle all sound target's media players
        recycleAllMediaPlayers()
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

        // Check whether need to pause
        checkWhetherIdle(event.values)

        // If particle view is not set to playing, don't continue
        if (particleView.state != ParticleView.PLAYING) return

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
                        Log.i("Media", "Recycling prepared player")
                        mediaPlayerPool.recyclePlayer(it)
                        orderedSoundTargets[i].mediaPlayerWithState = null
                        Log.i("Media", "Player recycled")
                    } else {
                        Log.i("Media", "Player not prepared, not recycling")
                    }
                }
            }
        }

        for (i in orderedSoundTargets.indices) {

            // If the closest target
            if (i === 0) {

                // Assign min angle
                minAngleFromSound = orderedSoundTargets[i].degreesFromAim
                Log.d("Minimum angle from sound", minAngleFromSound.toString())

                if (orderedSoundTargets[i].degreesFromAim <= primaryAngle) {
                    // Ensure focussing on target
                    if (focusTarget == null) {
                        startFocussing(orderedSoundTargets[i])
                    }
                }
            }

            // Check whether need to stop focussing
            if (
                orderedSoundTargets[i].degreesFromAim > primaryAngle &&
                focusTarget == orderedSoundTargets[i]
            ) {
                stopFocussing()
            }

            // Find targets that have no media player but need one
            if (
                orderedSoundTargets[i].mediaPlayerWithState === null &&
                i + 1 <= maxMediaPlayers &&
                orderedSoundTargets[i].degreesFromAim <= secondaryAngle
            ) {
                // If media player is available in the pool
                mediaPlayerPool.requestPlayer()?.let {mediaPlayerWithState ->

                    Log.i("Media", "Player requested")

                    // Assign to sound target
                    orderedSoundTargets[i].mediaPlayerWithState = mediaPlayerWithState

                    // Start playing sound resource
                    resources.openRawResourceFd(orderedSoundTargets[i].resID)?.let { assetFileDescriptor ->
                        mediaPlayerWithState.mediaPlayer.run {
                            setDataSource(assetFileDescriptor)
                            Log.i("Media", "Starting prepare async")
                            prepareAsync()
                        }
                    }
                }
            }

            // If sound target has media player
            orderedSoundTargets[i].mediaPlayerWithState?.mediaPlayer?.apply {

                // Calculate the new volume
                val volume: Float = if (isFocussed) {
                    if (focusTarget == orderedSoundTargets[i]) {
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

        // Set the background volume
        backgroundEffect.setVolume(calculateBackgroundVolume())
    }

    /**
     * Handle touch events
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {

        // If the action is a press down
        if (event.action === MotionEvent.ACTION_DOWN) {

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
        setVolumeForAllSoundTargets(0f)
    }

    /**
     * Recycle all media players and remove from sound targets (reset media pool)
     */
    fun recycleAllMediaPlayers() {

        // For each sound target that has a media player
        for (soundTarget in soundTargets) {
            soundTarget.mediaPlayerWithState?.let {

                // Recycle whether or not it's prepared
                mediaPlayerPool.recyclePlayer(it)

                // Set property to null
                soundTarget.mediaPlayerWithState = null
            }
        }
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

        // Volume relative to distance to sound centre
        if (minAngleFromSound < secondaryAngle) return 0.2f + 0.8f*(minAngleFromSound/secondaryAngle)

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
        focusCharacterDelay = timeToFocus/characterIndicesToDisplay.size

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

        // Pause the background effect
        backgroundEffect.pause()
    }

    /**
     * Sets the volume on every media player assigned to a sound target
     */
    fun setVolumeForAllSoundTargets(volume: Float) {

        // For each sound target that has a media player
        for (soundTarget in soundTargets) {
            soundTarget.mediaPlayerWithState?.let {

                // Set media player volume
                it.mediaPlayer.setVolume(volume, volume)
            }
        }
    }

    /**
     * Get an array of selected sound targets from the server and update soundTargets variable
     */
    fun updateSoundTargets(url: String) {

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

                // Set ready to start
                readyToStart = true

                // Update UI
                textView.text = resources.getString(R.string.start_message)
            },
            Response.ErrorListener {error ->

                // Update UI
                textView.text = resources.getString(R.string.connection_error)
            }
        )

        // Don't cache the request
        jsonObjectRequest.setShouldCache(false);

        // Add the request to the RequestQueue
        queue.add(jsonObjectRequest)
    }
}
