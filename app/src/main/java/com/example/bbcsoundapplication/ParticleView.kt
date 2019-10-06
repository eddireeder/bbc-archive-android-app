package com.example.bbcsoundapplication

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColor
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
    private val centreForce: Float = 1200f

    private val particleArray: Array<Particle?> = arrayOfNulls<Particle>(numParticles)
    private var centrePosition: FloatArray? = null
    private var currentFrameTimeNanos: Long = System.nanoTime()

    private val particleHandlerThread: HandlerThread = HandlerThread("ParticleHandlerThread")
    private var particleHandler: Handler? = null

    init {
        // Register choreographer frame callback
        Choreographer.getInstance().postFrameCallback(this)

        // Set the paint colour
        paint.color = Color.WHITE

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
                update()
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
     * Given time in nano seconds at last VSYNC
     */
    override fun doFrame(frameTimeNanos: Long) {
        // Update with latest frameTime
        currentFrameTimeNanos = frameTimeNanos

        // Send empty message to handler thread
        particleHandler?.sendEmptyMessage(1)

        // Reregister choreographer frame callback
        Choreographer.getInstance().postFrameCallback(this)
    }

    /**
     * Update the particle positions and velocities
     */
    fun update() {
        // Start updating once we have the centre position
        if (centrePosition == null) return

        // Calculate delta time in seconds
        val deltaTime: Float = (System.nanoTime() - currentFrameTimeNanos)*0.000000001f

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
            // Try to retrieve canvas
            val canvas: Canvas? = it.lockCanvas()

            // Do nothing if the surface is not created
            if (canvas == null) return

            // Clear the canvas
            canvas.drawColor(backgroundColour, PorterDuff.Mode.SRC_OVER)

            // Draw a circle for each particle
            for (particle in particleArray) {
                particle?.let {
                    canvas.drawCircle(it.position[0], it.position[1], particleRadius, paint)
                }
            }

            // Post canvas to surface
            it.unlockCanvasAndPost(canvas)
        }
    }
}