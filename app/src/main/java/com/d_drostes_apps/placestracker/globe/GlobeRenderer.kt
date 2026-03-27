package com.d_drostes_apps.placestracker.globe

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GlobeRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private lateinit var sphere: Sphere
    private var angleX = 0f
    private var angleY = 0f

    // For smooth transitions
    private var targetAngleX = 0f
    private var targetAngleY = 0f

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        sphere = Sphere(context)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        val ratio = width.toFloat() / height
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Simple interpolation for smooth rotation
        angleX += (targetAngleX - angleX) * 0.1f
        angleY += (targetAngleY - angleY) * 0.1f

        Matrix.setLookAtM(viewMatrix, 0,
            0f, 0f, 3f,
            0f, 0f, 0f,
            0f, 1f, 0f)

        Matrix.setIdentityM(modelMatrix, 0)
        // Adjusting rotation order and signs to match Lat/Lon expected behavior
        Matrix.rotateM(modelMatrix, 0, angleY, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, angleX, 0f, 1f, 0f)

        val temp = FloatArray(16)
        Matrix.multiplyMM(temp, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, temp, 0)

        sphere.draw(mvpMatrix)
    }

    fun rotate(dx: Float, dy: Float) {
        targetAngleX += dx * 0.5f
        targetAngleY += dy * 0.5f
    }

    fun setCameraPosition(lat: Float, lon: Float) {
        // Map Lat/Lon to rotation angles
        // Longitude affects rotation around Y axis (angleX)
        // Latitude affects rotation around X axis (angleY)
        targetAngleX = -lon
        targetAngleY = lat
    }
}