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

class ParticleView : SurfaceView, Choreographer.FrameCallback {

    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    private val backgroundColour: Int = ContextCompat.getColor(context, R.color.colorBackground)
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var circlePosition: FloatArray = floatArrayOf(200f, 200f)
    private var circleVelocity: FloatArray = floatArrayOf(-100f, 100f)

    private var currentFrameTimeNanos: Long = System.nanoTime()

    private val particleHandlerThread: HandlerThread = HandlerThread("ParticleHandlerThread")
    private var particleHandler: Handler? = null

    init {
        // Register choreographer frame callback
        Choreographer.getInstance().postFrameCallback(this)

        // Set the paint colour
        paint.color = Color.WHITE

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

        // Compute new acceleration (towards centre)
        val centrePosition: FloatArray = floatArrayOf(width/2f, height/2f)
        val acceleration: FloatArray = floatArrayOf(
            centrePosition[0] - circlePosition[0],
            centrePosition[1] - circlePosition[1]
        )

        // Compute the final velocity
        val finalVelocity: FloatArray = floatArrayOf(
            circleVelocity[0] + acceleration[0]*deltaTime,
            circleVelocity[1] + acceleration[1]*deltaTime
        )

        // Calculate new circle position
        val newPosition: FloatArray = floatArrayOf(
            circlePosition[0] + ((circleVelocity[0] + finalVelocity[0])/2)*deltaTime,
            circlePosition[1] + ((circleVelocity[1] + finalVelocity[1])/2)*deltaTime
        )

        // Replace velocity and position with new values
        circleVelocity = finalVelocity
        circlePosition = newPosition
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

            // Draw a circle
            canvas.drawCircle(circlePosition[0], circlePosition[1], 10f, paint)

            // Post canvas to surface
            it.unlockCanvasAndPost(canvas)
        }
    }
}