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

    private val backgroundColour: Int = ContextCompat.getColor(context, R.color.colorBackground)
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val numParticles: Int = 25
    private val particleRadius: Float = 10f
    private val maxSpeed: Float = 300f

    private val particleArray: Array<Particle?> = arrayOfNulls<Particle>(numParticles)
    private var centrePosition: FloatArray? = null
    private var previousFrameTimeNanos: Long = System.nanoTime()

    private val particleHandlerThread: HandlerThread = HandlerThread("ParticleHandlerThread")
    private var particleHandler: Handler? = null

    init {
        // Register choreographer frame callback
        Choreographer.getInstance().postFrameCallback(this)

        // Format the points
        paint.color = Color.WHITE
        paint.strokeWidth = particleRadius
        paint.strokeCap = Paint.Cap.ROUND

        // Initialise particles at (0, 0) with velocity of random distance and max speed
        for (i in 1..particleArray.size) {
            val velocity: FloatArray = floatArrayOf(Random.nextFloat(), Random.nextFloat())
            val magnitude: Float = sqrt(velocity[0].pow(2) + velocity[1].pow(2))
            val velocityWithMaxSpeed: FloatArray = floatArrayOf(
                (velocity[0]/magnitude)*maxSpeed,
                (velocity[1]/magnitude)*maxSpeed
            )
            particleArray[i - 1] = Particle(floatArrayOf(0f, 0f), velocityWithMaxSpeed)
        }

        // Initialise handler thread
        particleHandlerThread.start()
        particleHandler = object : Handler(particleHandlerThread.looper) {
            // Executed in the non-UI/background thread
            override fun handleMessage(msg: Message) {
                val timeDeltaNanos = msg.obj as Long
                update(timeDeltaNanos)
                draw()
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        // Store the view's centre
        centrePosition = floatArrayOf(width/2f, height/2f)

        // Place particles at random positions
        for (particle in particleArray) {
            particle?.let {
                val initialPosition: FloatArray = floatArrayOf(
                    (0..width).random().toFloat(),
                    (0..height).random().toFloat()
                )
                it.position = initialPosition
            }
        }
    }

    /**
     * Called on initial layout and any screen size changes
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        centrePosition = floatArrayOf(w/2f, h/2f)
    }

    /**
     * Given time in nano seconds that frame started rendering
     */
    override fun doFrame(frameTimeNanos: Long) {

        // Calculate the delay in milliseconds
        val delay: Float = (System.nanoTime() - frameTimeNanos)*0.000001f

        Log.i("Frame delay", ((System.nanoTime() - frameTimeNanos)*0.000001f).toString())

        // TODO: Skip a frame if delay is greater than ~10-15 ms

        // Calculate difference between last frame and current frame
        val timeDeltaNanos: Long = frameTimeNanos - previousFrameTimeNanos

        // Update previous frame time
        previousFrameTimeNanos = frameTimeNanos

        // Send message to handler thread with time delta
        val message = Message()
        message.obj = timeDeltaNanos
        particleHandler?.sendMessage(message)

        // Reregister choreographer frame callback
        Choreographer.getInstance().postFrameCallback(this)
    }

    /**
     * Update the particle positions and velocities
     */
    fun update(timeDeltaNanos: Long) {
        // Start updating once we have the centre position
        if (centrePosition == null) return

        // Calculate delta time in seconds
        val deltaTime: Float = timeDeltaNanos*0.000000001f

        // Calculate the current centre force
        val mainActivity: MainActivity = context as MainActivity
        val centreForce = if (mainActivity.minAngleFromSound < mainActivity.secondaryAngle) {
            300f + 1200f*((mainActivity.secondaryAngle - mainActivity.minAngleFromSound)/mainActivity.secondaryAngle)
        } else {
            300f
        }

        for (particle in particleArray) {
            particle?.let {

                // Calculate vector to centre
                val toCentre: FloatArray = floatArrayOf(
                    centrePosition!![0] - it.position[0],
                    centrePosition!![1] - it.position[1]
                )

                // Calculate magnitude
                val toCentreMagnitude: Float = sqrt(toCentre[0].pow(2) + toCentre[1].pow(2))

                // Normalise the vector and multiply by acceleration constant to get acceleration
                val acceleration: FloatArray = floatArrayOf(
                    (toCentre[0]/toCentreMagnitude)*centreForce,
                    (toCentre[1]/toCentreMagnitude)*centreForce
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
            canvas.drawPoints(pointPositions, paint)

            // Post canvas to surface
            it.unlockCanvasAndPost(canvas)
        }
    }
}