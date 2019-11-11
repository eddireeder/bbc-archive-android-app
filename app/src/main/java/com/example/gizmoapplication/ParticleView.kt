package com.example.gizmoapplication

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class ParticleView : SurfaceView, Choreographer.FrameCallback {

    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    companion object {
        const val INITIALISING: Int = 0
        const val PAUSING: Int = 1
        const val PAUSED: Int = 2
        const val PLAYING: Int = 3
    }

    var state: Int = INITIALISING
    private val distanceOutside: Float = 50f
    private val exitForce: Float = 500f

    private val backgroundColour: Int = ContextCompat.getColor(context, R.color.background)
    private val particleColour: Int = ContextCompat.getColor(context, R.color.particle)
    private val textColour: Int = ContextCompat.getColor(context, R.color.text)
    private val particlePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val numParticles: Int = 100
    private val particleRadius: Float = 10f
    private val maxSpeed: Float = 300f
    private val randomForceConstant: Float = 250f
    private val centreForceLowerLimit: Float = 500f
    private val centreForceUpperLimit: Float = 2000f

    private val particleArray: Array<Particle?> = arrayOfNulls<Particle>(numParticles)
    private var centrePosition: FloatArray? = null

    private var previousFrameTimeNanos: Long = System.nanoTime()

    private val fpsTimesNanos: MutableList<Long> = mutableListOf<Long>(System.nanoTime())
    private val fpsTimesMaxSize: Int = 50
    private val maximumFrameDelay: Int = 15
    private var fps: Int = 0

    private val particleHandlerThread: HandlerThread = HandlerThread("ParticleHandlerThread")
    private var particleHandler: Handler? = null

    private val mainActivity: MainActivity = context as MainActivity

    init {
        // Register choreographer frame callback
        Choreographer.getInstance().postFrameCallback(this)

        // Format text
        textPaint.color = textColour
        textPaint.textSize = 50f

        // Format the points
        particlePaint.color = particleColour
        particlePaint.strokeWidth = particleRadius
        particlePaint.strokeCap = Paint.Cap.ROUND

        // Initialise handler thread
        particleHandlerThread.start()
        particleHandler = object : Handler(particleHandlerThread.looper) {

            // Executed in the non-UI/background thread
            override fun handleMessage(msg: Message) {

                // Retrieve choreographer frame time from message
                val frameTimeNanos = msg.obj as Long

                // Get current time in nano seconds
                val currentTimeNanos = System.nanoTime()

                // Calculate the delay in milliseconds
                val delay: Float = (currentTimeNanos - frameTimeNanos)*0.000001f

                // Skip the frame if the delay meets a threshold
                if (delay > maximumFrameDelay) return

                // Calculate difference between previous frame and current frame
                val deltaTimeNanos: Long = frameTimeNanos - previousFrameTimeNanos

                // Update previous frame time as current
                previousFrameTimeNanos = frameTimeNanos

                // Update FPS counter (using device clock though)
                fpsTimesNanos.add(currentTimeNanos)
                val difference: Float = (currentTimeNanos - fpsTimesNanos.first())*0.000000001f
                if (fpsTimesNanos.size > fpsTimesMaxSize) fpsTimesNanos.removeAt(0)
                fps = if (difference > 0) (fpsTimesNanos.size/difference).toInt() else 0

                // Update particles
                update(deltaTimeNanos)

                // Draw particles
                draw()
            }
        }
    }

    /**
     * Called on initial layout of the view
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        // Store the view's centre
        centrePosition = floatArrayOf(width/2f, height/2f)

        // Initialise particles at random positions around the outside with random velocity and force
        for (i in particleArray.indices) {

            // Generate random velocity
            val randomVelocityDirection: FloatArray = generateRandomUnitVector()
            val randomVelocityWithMaxSpeed: FloatArray = floatArrayOf(
                randomVelocityDirection[0]*maxSpeed,
                randomVelocityDirection[1]*maxSpeed
            )

            // Generate random force
            val randomForceDirection: FloatArray = generateRandomUnitVector()
            val randomForce: FloatArray = floatArrayOf(
                randomForceDirection[0]*randomForceConstant,
                randomForceDirection[1]*randomForceConstant
            )

            // Generate random initial position
            val heightAndSide: Float = Random.nextFloat()*(2*height)
            val initialPosition: FloatArray = if (heightAndSide < height) {
                floatArrayOf(
                    -distanceOutside,
                    heightAndSide
                )
            } else {
                floatArrayOf(
                    width + distanceOutside,
                    heightAndSide - height
                )
            }

            // Initialise particle
            particleArray[i] = Particle(initialPosition, randomVelocityWithMaxSpeed, randomForce)
        }

        // Update view state from initialised to paused
        state = PAUSED
    }

    /**
     * Called on initial layout and any screen size changes
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {

        // Update centre position
        centrePosition = floatArrayOf(w/2f, h/2f)
    }

    /**
     * Given time in nano seconds that frame started rendering
     */
    override fun doFrame(frameTimeNanos: Long) {

        // Re-register choreographer frame callback
        Choreographer.getInstance().postFrameCallback(this)

        // Send message to handler thread with time delta
        val message = Message()
        message.obj = frameTimeNanos
        particleHandler?.sendMessage(message)
    }

    /**
     * Update the particle positions and velocities
     */
    fun update(deltaTimeNanos: Long) {

        // No need to update if paused or initialising
        if (state == PAUSED || state == INITIALISING) return

        // Calculate delta time in seconds
        val deltaTime: Float = deltaTimeNanos*0.000000001f

        // Update particles for pausing
        if (state == PAUSING) pausingUpdate(deltaTime)

        // Update particles for playing
        else if (state == PLAYING) playingUpdate(deltaTime)
    }

    /**
     * Update view based on the state being PAUSING
     */
    fun pausingUpdate(deltaTime: Float) {

        // Initialise boolean to know when view can pause
        var readyToPause: Boolean = true

        for (particle in particleArray) {
            particle?.let particleBlock@{

                // Check whether particle is outside the width of the screen
                if (it.position[0] < -distanceOutside || it.position[0] > (width + distanceOutside)) return@particleBlock

                // Check whether particle is outside the height of the screen
                if (it.position[1] < -distanceOutside || it.position[1] > (height + distanceOutside)) return@particleBlock

                // Not ready to pause as particle is still on screen
                if (readyToPause) readyToPause = false

                // Calculate vector to initial position/exit
                val toExit: FloatArray = floatArrayOf(
                    it.initialPosition[0] - it.position[0],
                    it.initialPosition[1] - it.position[1]
                )

                // Calculate the magnitude
                val toExitMagnitude: Float = sqrt(toExit[0].pow(2) + toExit[1].pow(2))

                // Compute acceleration using exit force
                val acceleration: FloatArray = floatArrayOf(
                    (toExit[0]/toExitMagnitude)*exitForce,
                    (toExit[1]/toExitMagnitude)*exitForce
                )

                // Compute the final velocity
                val finalVelocity: FloatArray = floatArrayOf(
                    it.velocity[0] + acceleration[0]*deltaTime,
                    it.velocity[1] + acceleration[1]*deltaTime
                )

                // Limit magnitude to max speed
                val finalVelocityMagnitude = sqrt(finalVelocity[0].pow(2) + finalVelocity[1].pow(2))

                if (finalVelocityMagnitude > maxSpeed) {
                    finalVelocity[0] = (finalVelocity[0]/finalVelocityMagnitude)*maxSpeed
                    finalVelocity[1] = (finalVelocity[1]/finalVelocityMagnitude)*maxSpeed
                }

                // Calculate new circle position
                val newPosition: FloatArray = floatArrayOf(
                    it.position[0] + ((it.velocity[0] + finalVelocity[0])/2)*deltaTime,
                    it.position[1] + ((it.velocity[1] + finalVelocity[1])/2)*deltaTime
                )

                // Replace velocity and position with new values
                it.velocity = finalVelocity
                it.position = newPosition
            }
        }

        if (readyToPause) {

            // Reset the velocity of each particle to it's initial velocity (random)
            for (particle in particleArray) particle?.let { it.velocity = it.initialVelocity }

            // Set state to paused
            state = PAUSED
        }
    }

    /**
     * Update view based on the state being PLAYING
     */
    fun playingUpdate(deltaTime: Float) {
/*
        // Calculate the centre force
        val centreForce: Float = if (mainActivity.soundTargetManager.orderedSoundTargets.size > 0) {

            // Create a variable to reference closest sound for readibility
            val closestSoundTarget: SoundTarget = mainActivity.soundTargetManager.orderedSoundTargets[0]

            if (closestSoundTarget.degreesFromAim < mainActivity.configuration.primaryAngle) {
                centreForceUpperLimit
            } else if (closestSoundTarget.degreesFromAim < mainActivity.configuration.secondaryAngle) {
                centreForceLowerLimit + ((mainActivity.configuration.secondaryAngle - mainActivity.configuration.primaryAngle - closestSoundTarget.degreesFromAim)/(mainActivity.configuration.secondaryAngle - mainActivity.configuration.primaryAngle))*(centreForceUpperLimit - centreForceLowerLimit)
            } else {
                centreForceLowerLimit
            }
        } else {
            centreForceLowerLimit
        }

 */
        val centreForce: Float = centreForceLowerLimit

        for (particle in particleArray) {
            particle?.let {

                // Calculate vector to centre
                val toCentre: FloatArray = floatArrayOf(
                    centrePosition!![0] - it.position[0],
                    centrePosition!![1] - it.position[1]
                )

                // Calculate magnitude
                val toCentreMagnitude: Float = sqrt(toCentre[0].pow(2) + toCentre[1].pow(2))

                // Compute acceleration using centre force and random force
                val acceleration: FloatArray = floatArrayOf(
                    (toCentre[0]/toCentreMagnitude)*centreForce + it.randomForce[0],
                    (toCentre[1]/toCentreMagnitude)*centreForce + it.randomForce[1]
                )

                // Compute the final velocity
                val finalVelocity: FloatArray = floatArrayOf(
                    it.velocity[0] + acceleration[0]*deltaTime,
                    it.velocity[1] + acceleration[1]*deltaTime
                )

                // Limit magnitude to max speed
                val finalVelocityMagnitude = sqrt(finalVelocity[0].pow(2) + finalVelocity[1].pow(2))

                if (finalVelocityMagnitude > maxSpeed) {
                    finalVelocity[0] = (finalVelocity[0]/finalVelocityMagnitude)*maxSpeed
                    finalVelocity[1] = (finalVelocity[1]/finalVelocityMagnitude)*maxSpeed
                }

                // Calculate new circle position
                val newPosition: FloatArray = floatArrayOf(
                    it.position[0] + ((it.velocity[0] + finalVelocity[0])/2)*deltaTime,
                    it.position[1] + ((it.velocity[1] + finalVelocity[1])/2)*deltaTime
                )

                // Replace velocity and position with new values
                it.velocity = finalVelocity
                it.position = newPosition
            }
        }
    }

    /**
     * Draw the latest particle positions
     */
    fun draw() {

        // Draw circle at its position
        holder?.let {
            // Extract particle positions as array
            val pointPositions: FloatArray = FloatArray(numParticles*2)

            for (i in 0 until numParticles) {
                particleArray[i]?.let {
                    pointPositions[2*i] = it.position[0]
                    pointPositions[2*i + 1] = it.position[1]
                }
            }

            // Try to retrieve canvas
            val canvas: Canvas? = it.lockCanvas()

            // Do nothing if the surface is not created
            if (canvas == null) return

            // Clear the canvas
            canvas.drawColor(backgroundColour, PorterDuff.Mode.SRC_OVER)

            // Draw a point for each particle
            canvas.drawPoints(pointPositions, particlePaint)

            // Draw current FPS if debug mode on
            if (mainActivity.debugMode) canvas.drawText(fps.toString(), 20f, 60f, textPaint)

            // Post canvas to surface
            it.unlockCanvasAndPost(canvas)
        }
    }

    /**
     * Start playing the particle simulation (particles enter from the side)
     */
    fun play() {

        // Update state
        if (state == PAUSED) state = PLAYING
    }

    /**
     * Start pausing process (particles will fly out to the side)
     */
    fun pause() {

        // Update state
        state = PAUSING
    }

    /**
     * A helper function to generate a random direction/unit vector
     */
    fun generateRandomUnitVector(): FloatArray {

        val vector: FloatArray = floatArrayOf(Random.nextFloat() - 0.5f, Random.nextFloat() - 0.5f)
        val magnitude: Float = sqrt(vector[0].pow(2) + vector[1].pow(2))
        return floatArrayOf(
            vector[0]/magnitude,
            vector[1]/magnitude
        )
    }
}