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

    private val numParticles: Int = 50
    private val particleRadius: Float = 10f
    private val maxSpeed: Float = 300f
    private val maxSteeringForce: Float = 300f
    private val randomForceConstant: Float = 250f

    private val desiredSeparation: Float = 100f
    private val neighbourDistance: Float = 200f
    private val separationWeight: Float = 2f
    private val alignmentWeight: Float = 1f
    private val cohesionWeight: Float = 0.5f
    private val borderRadiusUpperLimit: Float = 50f
    private val borderRadiusLowerLimit: Float = 400f
    private val borderForceWeight: Float = 5f

    private val particleArray: Array<Particle?> = arrayOfNulls(numParticles)
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

        // Calculate where the centre position should be (between the description and top of phone)
        centrePosition = floatArrayOf(width/2f, (height/2f) - 200f)

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

        // Calculate the border radius
        val borderRadius: Float = if (mainActivity.soundTargetManager.orderedSoundTargets.size > 0) {

            // Create a variable to reference closest sound for readibility
            val closestSoundTarget: SoundTarget = mainActivity.soundTargetManager.orderedSoundTargets[0]

            if (closestSoundTarget.degreesFromAim < mainActivity.configuration.primaryAngle) {
                borderRadiusUpperLimit
            } else if (closestSoundTarget.degreesFromAim < mainActivity.configuration.secondaryAngle) {
                borderRadiusLowerLimit + ((mainActivity.configuration.secondaryAngle - mainActivity.configuration.primaryAngle - closestSoundTarget.degreesFromAim)/(mainActivity.configuration.secondaryAngle - mainActivity.configuration.primaryAngle))*(borderRadiusUpperLimit - borderRadiusLowerLimit)
            } else {
                borderRadiusLowerLimit
            }
        } else {
            borderRadiusLowerLimit
        }

        for (particle in particleArray) {
            particle?.let {

                // Calculate forces from 3 boids rules
                val separationForce: FloatArray = separation(it)
                val alignmentForce: FloatArray = alignment(it)
                val cohesionForce: FloatArray = cohesion(it)

                // Calculate border force
                val borderForce: FloatArray = borderForce(it, borderRadius)

                // Calculate acceleration from weighted forces
                val acceleration: FloatArray = floatArrayOf(
                    separationForce[0]*separationWeight + alignmentForce[0]*alignmentWeight + cohesionForce[0]*cohesionWeight + borderForce[0]*borderForceWeight,
                    separationForce[1]*separationWeight + alignmentForce[1]*alignmentWeight + cohesionForce[1]*cohesionWeight + borderForce[1]*borderForceWeight
                )

                // Compute the new velocity
                val newVelocity: FloatArray = floatArrayOf(
                    it.velocity[0] + acceleration[0]*deltaTime,
                    it.velocity[1] + acceleration[1]*deltaTime
                )

                // Limit magnitude to max speed
                val finalVelocityMagnitude = sqrt(newVelocity[0].pow(2) + newVelocity[1].pow(2))
                if (finalVelocityMagnitude > maxSpeed) {
                    newVelocity[0] = (newVelocity[0]/finalVelocityMagnitude)*maxSpeed
                    newVelocity[1] = (newVelocity[1]/finalVelocityMagnitude)*maxSpeed
                }

                // Calculate new position
                val newPosition: FloatArray = floatArrayOf(
                    it.position[0] + ((it.velocity[0] + newVelocity[0])/2)*deltaTime,
                    it.position[1] + ((it.velocity[1] + newVelocity[1])/2)*deltaTime
                )

                // Update velocity and position
                it.velocity = newVelocity
                it.position = newPosition
            }
        }
    }

    /**
     * Calculate force for the seperation step
     */
    fun separation(currentParticle: Particle): FloatArray {

        var steerVector: FloatArray = floatArrayOf(0f, 0f)
        var count: Int = 0

        for (p in particleArray) {
            p?.let {

                // If not the current particle
                if (p != currentParticle) {

                    // Calculate the distance between them
                    val distance: Float = distanceBetween(p.position, currentParticle.position)

                    // If less than desired separation
                    if (distance < desiredSeparation) {

                        // Calculate the direction vector pointing away from neighbour
                        val directionAway: FloatArray = normalise(floatArrayOf(
                            currentParticle.position[0] - p.position[0],
                            currentParticle.position[1] - p.position[1]
                        ))

                        // Weight by distance and add to steering vector
                        steerVector[0] += directionAway[0]/distance
                        steerVector[1] += directionAway[1]/distance

                        // Increment count
                        count++
                    }
                }
            }
        }

        // Divide by count if necessary
        if (count > 0) {
            steerVector[0] = steerVector[0]/count
            steerVector[1] = steerVector[1]/count

            // If our steer vector has a positive magnitude
            val magnitude: Float = calculateMagnitude(steerVector)
            if (magnitude > 0) {

                // Set the desired velocity as the steer vector with max speed
                val desiredVelocity: FloatArray = floatArrayOf(
                    (steerVector[0]/magnitude)*maxSpeed,
                    (steerVector[1]/magnitude)*maxSpeed
                )

                // Calculate steering force
                return calculateSteeringForce(currentParticle.velocity, desiredVelocity)
            }
        }

        // No force
        return floatArrayOf(0f, 0f)
    }

    /**
     * Calculate force for the alignment step
     */
    fun alignment(currentParticle: Particle): FloatArray {

        val averageVelocity: FloatArray = floatArrayOf(0f, 0f)
        var count: Int = 0

        for (p in particleArray) {
            p?.let {

                // If not the current particle
                if (p != currentParticle) {

                    // If particle is a neighbour
                    if (distanceBetween(p.position, currentParticle.position) < neighbourDistance) {

                        // Add to average velocity
                        averageVelocity[0] += p.velocity[0]
                        averageVelocity[1] += p.velocity[1]

                        // Increment count
                        count++
                    }
                }
            }
        }

        // If we actually had neighbours
        if (count > 0) {

            // Divide by number of neighbours
            averageVelocity[0] = averageVelocity[0]/count
            averageVelocity[1] = averageVelocity[1]/count

            // Set desired velocity as the average velocity with max speed
            val desiredVelocity: FloatArray = normalise(averageVelocity)
            desiredVelocity[0] = desiredVelocity[0]*maxSpeed
            desiredVelocity[1] = desiredVelocity[1]*maxSpeed

            // Calculate steering force
            return calculateSteeringForce(currentParticle.velocity, desiredVelocity)
        }

        // No force
        return floatArrayOf(0f, 0f)
    }

    /**
     * Calculate force for the cohesion step
     */
    fun cohesion(currentParticle: Particle): FloatArray {

        val perceivedCentre: FloatArray = floatArrayOf(0f, 0f)
        var count: Int = 0

        for (p in particleArray) {
            p?.let {

                // If not the current particle
                if (p != currentParticle) {

                    // If particle is a neighbour
                    if (distanceBetween(p.position, currentParticle.position) < neighbourDistance) {

                        // Add position to perceived centre
                        perceivedCentre[0] += p.position[0]
                        perceivedCentre[1] += p.position[1]

                        // Increment count
                        count++
                    }
                }
            }
        }

        // If we do have neighbours
        if (count > 0) {

            // Divide by number of neighbours
            perceivedCentre[0] = perceivedCentre[0]/count
            perceivedCentre[1] = perceivedCentre[1]/count

            // Return steering force towards position
            return seek(currentParticle, perceivedCentre)

        } else {

            // No force
            return floatArrayOf(0f, 0f)
        }
    }

    /**
     * Calculate steering force to seek a target
     * Steering force = desired velocity - current velocity
     */
    fun seek(currentParticle: Particle, target: FloatArray): FloatArray {

        val desiredVelocity: FloatArray = normalise(floatArrayOf(
            target[0] - currentParticle.position[0],
            target[1] - currentParticle.position[1]
        ))

        // Limit desired velocity to max speed
        desiredVelocity[0] = desiredVelocity[0]*maxSpeed
        desiredVelocity[1] = desiredVelocity[1]*maxSpeed

        // Calculate steering force
        return calculateSteeringForce(currentParticle.velocity, desiredVelocity)
    }

    /**
     * Calculate force to keep particles within a border
     */
    fun borderForce(currentParticle: Particle, borderRadius: Float): FloatArray {

        return if (distanceBetween(currentParticle.position, centrePosition!!) > borderRadius) {
            seek(currentParticle, centrePosition!!)
        } else {
            floatArrayOf(0f, 0f)
        }
    }

    /**
     * Helper function to calculate steering force for a desired velocity
     */
    fun calculateSteeringForce(currentVelocity: FloatArray, desiredVelocity: FloatArray): FloatArray {

        // Calculate steering force
        val steerForce: FloatArray = floatArrayOf(
            desiredVelocity[0] - currentVelocity[0],
            desiredVelocity[1] - currentVelocity[1]
        )

        // Limit to max steering force
        val magnitude = calculateMagnitude(steerForce)
        return if (magnitude > maxSteeringForce) {
            floatArrayOf(
                (steerForce[0]/magnitude)*maxSteeringForce,
                (steerForce[1]/magnitude)*maxSteeringForce
            )
        } else steerForce
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
     * A helper function to calculate the magnitude of a vector
     */
    fun calculateMagnitude(vector: FloatArray) =
        sqrt(vector[0].pow(2) + vector[1].pow(2))

    /**
     * A helper function to normalise a vector
     */
    fun normalise(vector: FloatArray): FloatArray {

        val magnitude: Float = sqrt(vector[0].pow(2) + vector[1].pow(2))
        return floatArrayOf(
            vector[0]/magnitude,
            vector[1]/magnitude
        )
    }

    /**
     * A helper function to generate a random direction/unit vector
     */
    fun generateRandomUnitVector(): FloatArray {

        val vector: FloatArray = floatArrayOf(Random.nextFloat() - 0.5f, Random.nextFloat() - 0.5f)
        return normalise(vector)
    }

    /**
     * A helper function to calculate the distance between two position vectors
     */
    fun distanceBetween(position1: FloatArray, position2: FloatArray): Float =
        sqrt((position2[0] - position1[0]).pow(2) + (position2[1] - position1[1]).pow(2))
}