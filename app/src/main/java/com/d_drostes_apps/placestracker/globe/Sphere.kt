package com.d_drostes_apps.placestracker.globe

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import com.d_drostes_apps.placestracker.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Sphere(private val context: Context) {

    private var program: Int
    private var textureId: Int
    private var vertexBuffer: FloatBuffer
    private var texBuffer: FloatBuffer
    private var vertexCount = 0

    init {
        val (vData, tData) = createSphereData(1.0f, 40, 40)
        vertexCount = vData.size / 3
        
        vertexBuffer = ByteBuffer.allocateDirect(vData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vData).position(0)
        
        texBuffer = ByteBuffer.allocateDirect(tData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        texBuffer.put(tData).position(0)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, """
            attribute vec4 vPosition;
            attribute vec2 vTexCoord;
            varying vec2 texCoord;
            uniform mat4 uMVPMatrix;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                texCoord = vTexCoord;
            }
        """.trimIndent())

        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, """
            precision mediump float;
            varying vec2 texCoord;
            uniform sampler2D texture;
            void main() {
                gl_FragColor = texture2D(texture, texCoord);
            }
        """.trimIndent())

        program = GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vertexShader)
            GLES20.glAttachShader(this, fragmentShader)
            GLES20.glLinkProgram(this)
        }

        textureId = loadTexture(context, R.drawable.earth_texture)
    }

    fun draw(mvp: FloatArray) {
        GLES20.glUseProgram(program)
        val posHandle = GLES20.glGetAttribLocation(program, "vPosition")
        val texHandle = GLES20.glGetAttribLocation(program, "vTexCoord")
        val mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }

    private fun createSphereData(radius: Float, stacks: Int, slices: Int): Pair<FloatArray, FloatArray> {
        val vertices = mutableListOf<Float>()
        val texCoords = mutableListOf<Float>()

        for (i in 0 until stacks) {
            val phi1 = Math.PI * i / stacks
            val phi2 = Math.PI * (i + 1) / stacks

            for (j in 0 until slices) {
                val theta1 = 2.0 * Math.PI * j / slices
                val theta2 = 2.0 * Math.PI * (j + 1) / slices

                // Triangle 1
                addVertex(vertices, texCoords, radius, phi1, theta1, j, i, slices, stacks)
                addVertex(vertices, texCoords, radius, phi2, theta1, j, i + 1, slices, stacks)
                addVertex(vertices, texCoords, radius, phi1, theta2, j + 1, i, slices, stacks)

                // Triangle 2
                addVertex(vertices, texCoords, radius, phi1, theta2, j + 1, i, slices, stacks)
                addVertex(vertices, texCoords, radius, phi2, theta1, j, i + 1, slices, stacks)
                addVertex(vertices, texCoords, radius, phi2, theta2, j + 1, i + 1, slices, stacks)
            }
        }
        return Pair(vertices.toFloatArray(), texCoords.toFloatArray())
    }

    private fun addVertex(v: MutableList<Float>, t: MutableList<Float>, r: Float, phi: Double, theta: Double, sj: Int, si: Int, slices: Int, stacks: Int) {
        v.add((r * Math.sin(phi) * Math.cos(theta)).toFloat())
        v.add((r * Math.cos(phi)).toFloat())
        v.add((r * Math.sin(phi) * Math.sin(theta)).toFloat())
        t.add(sj.toFloat() / slices)
        t.add(si.toFloat() / stacks)
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun loadTexture(context: Context, resId: Int): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val bitmap = BitmapFactory.decodeResource(context.resources, resId) ?: return 0
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        bitmap.recycle()
        return textureIds[0]
    }
}