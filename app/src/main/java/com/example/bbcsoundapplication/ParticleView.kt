package com.example.bbcsoundapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

class ParticleView : SurfaceView, SurfaceHolder.Callback, Runnable {

    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        holder.addCallback(this)
        paint.color = Color.WHITE
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        // Test draw on surface create
        holder?.let {
            val canvas: Canvas = it.lockCanvas()
            canvas.drawCircle(100f, 100f, 50f, paint)
            it.unlockCanvasAndPost(canvas)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {

    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {

    }

    override fun run() {

    }
}