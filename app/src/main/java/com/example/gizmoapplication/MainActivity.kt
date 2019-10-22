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
import android.widget.TextView
import androidx.core.os.postDelayed
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Text

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var rotationVectorSensor: Sensor

    private val soundTargets: MutableList<SoundTarget> = mutableListOf()
    val primaryAngle: Float = 5f
    val secondaryAngle: Float = 30f
    private lateinit var backgroundEffect: BackgroundEffect

    private val maxMediaPlayers: Int = 4
    private val mediaPlayerPool = MediaPlayerPool(maxMediaPlayers)

    var minAngleFromSound: Float = 180f

    private val timeToFocus: Float = 5f
    private var focusTarget: SoundTarget? = null
    private var isFocussed: Boolean = false
    private lateinit var vibrator: Vibrator

    private val focusTimerHandler: Handler = Handler()
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

        Log.d("Startup", "Content view set")

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

        Log.d("Startup", "Sensor initialised")

        // Initialise static background sound and start playing
        backgroundEffect = BackgroundEffect(this)

        Log.d("Startup", "Background effect started")

        // Initialise vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        Log.d("Startup", "Vibrator initialised")

        // Retrieve views
        particleView = findViewById<ParticleView>(R.id.particleView)
        descriptionTextView = findViewById<TextView>(R.id.description)
        categoryTextView = findViewById<TextView>(R.id.category)
        trackInfoTextView = findViewById<TextView>(R.id.trackInfo)
        textView = findViewById<TextView>(R.id.textView)

        // Initialise focus timer
        focusTimerRunnable = object: Runnable {
            override fun run() = focusTimerUpdate()
        }

        Log.d("Startup", "Runnable initialised")

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

        // Resume the static effect
        backgroundEffect.resume()
    }

    /**
     * Called on pausing of activity
     */
    override fun onPause() {

        super.onPause()

        // Unregister this listener
        sensorManager.unregisterListener(this)

        // Pause the static effect
        backgroundEffect.pause()
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

        Log.d("Sensor values", "${event.values[0].toString()} ${event.values[1].toString()} ${event.values[2].toString()}")

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
        backgroundEffect.setVolume(calculateBackgroundVolume(minAngleFromSound, secondaryAngle))
    }

    /**
     * Handle touch events
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {

        // If the action is a press down
        if (event.action === MotionEvent.ACTION_DOWN) {
            Log.d("particle view state", particleView.state.toString())
            if (particleView.state == 2) particleView.play()
            else if (particleView.state == 3) particleView.pause()
        }
        return true
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
    fun calculateBackgroundVolume(minAngleFromSound: Float, secondaryAngle: Float): Float {

        if (minAngleFromSound < secondaryAngle) {
            return 0.2f + 0.8f*(minAngleFromSound/secondaryAngle)
        } else {
            return 1f
        }
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
        focusTimerHandler.postDelayed(focusTimerRunnable, 0)
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
        focusTimerHandler.removeCallbacks(focusTimerRunnable)

        // Update text views
        descriptionTextView.text = ""
        categoryTextView.text = ""
        trackInfoTextView.text = ""

        // Resume background effect
        backgroundEffect.resume()
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
            focusTimerHandler.postDelayed(focusTimerRunnable, (focusCharacterDelay * 1000f).toLong())

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

                Log.d("Startup", "Sound targets retrieved")
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
