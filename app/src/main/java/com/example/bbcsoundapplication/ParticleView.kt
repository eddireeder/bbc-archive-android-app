package com.example.bbcsoundapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView

class ParticleView : SurfaceView, Choreographer.FrameCallback {

    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var circlePosition: FloatArray = floatArrayOf(0f, 0f)
    private var circleVelocity: FloatArray = floatArrayOf(100f, 100f)

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
        val deltaTimeNano: Float = (System.nanoTime() - currentFrameTimeNanos)*0.000000001f

        // Calculate new circle x position
        circlePosition[0] += circleVelocity[0]*deltaTimeNano

        // Calculate new circle y position
        circlePosition[1] += circleVelocity[1]*deltaTimeNano
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
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            // Draw a circle
            canvas.drawCircle(circlePosition[0], circlePosition[1], 50f, paint)

            // Post canvas to surface
            it.unlockCanvasAndPost(canvas)
        }
    }
}