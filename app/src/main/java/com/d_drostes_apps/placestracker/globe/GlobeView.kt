package com.d_drostes_apps.placestracker.globe

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent

class GlobeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer: GlobeRenderer

    init {
        setEGLContextClientVersion(2)
        renderer = GlobeRenderer(context)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    private var previousX = 0f
    private var previousY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val dx = x - previousX
                val dy = y - previousY

                renderer.rotate(dx, dy)
            }
        }

        previousX = x
        previousY = y
        return true
    }

    fun setCameraPosition(lat: Float, lon: Float) {
        renderer.setCameraPosition(lat, lon)
    }
}