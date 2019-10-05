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

class ParticleView : SurfaceView, Choreographer.FrameCallback {

    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    private val backgroundColour: Int = ContextCompat.getColor(context, R.color.colorBackground)
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val numParticles: Int = 10
    private val particleRadius: Float = 10f
    private val centreRadius: Float = 400f
    private val boundaryForceConstant: Float = 50f
    private val particleArray: Array<Particle?> = arrayOfNulls<Particle>(numParticles)
    private var centrePosition: FloatArray = floatArrayOf(0f, 0f)
    private var currentFrameTimeNanos: Long = System.nanoTime()

    private val particleHandlerThread: HandlerThread = HandlerThread("ParticleHandlerThread")
    private var particleHandler: Handler? = null

    init {
        // Register choreographer frame callback
        Choreographer.getInstance().postFrameCallback(this)

        // Set the paint colour
        paint.color = Color.WHITE

        // Initialise particles at (0, 0)
        for (i in 1..particleArray.size) {
            particleArray[i - 1] = Particle(floatArrayOf(0f, 0f))
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
        // Calculate delta time in seconds
        val deltaTime: Float = (System.nanoTime() - currentFrameTimeNanos)*0.000000001f

        for (particle in particleArray) {
            particle?.let {
                // Calculate vector to centre
                val toCentre: FloatArray = floatArrayOf(
                    centrePosition[0] - it.position[0],
                    centrePosition[1] - it.position[1]
                )

                // Compute the distance/magnitude of this vector
                val magnitude: Float = sqrt(toCentre[0].pow(2) + toCentre[1].pow(2))

                // If outside the boundary, accelerate towards the centre
                val acceleration: FloatArray = if (magnitude > centreRadius) {
                    floatArrayOf(
                        (toCentre[0]/magnitude)*boundaryForceConstant,
                        (toCentre[1]/magnitude)*boundaryForceConstant
                    )
                } else {
                    floatArrayOf(
                        0f,
                        0f
                    )
                }

                // Compute the final velocity
                val finalVelocity: FloatArray = floatArrayOf(
                    it.velocity[0] + acceleration[0]*deltaTime,
                    it.velocity[1] + acceleration[1]*deltaTime
                )

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