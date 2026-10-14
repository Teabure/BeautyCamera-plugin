package com.mytools.beautycamera

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraRenderer(private val view: GLCameraView) : GLSurfaceView.Renderer {

    private var oesTexId = -1
    var surfaceTexture: SurfaceTexture? = null
        private set
    private var program = 0
    private var aPosition = 0
    private var aTextureCoord = 0
    private var uTexMatrix = 0
    private var uTexture = 0
    private var uBeauty = 0
    private var uWhite = 0
    private var uTexel = 0

    private val texMatrix = FloatArray(16)
    private val rotationMatrix = FloatArray(16)
    private val finalMatrix = FloatArray(16)

    private val vertexBuffer: FloatBuffer
    private val texBuffer: FloatBuffer

    @Volatile var beautyLevel = 0.0f
    @Volatile var whiteLevel = 0.0f
    var width = 0
    var height = 0

    @Volatile var captureRequested = false
    @Volatile var captureCallback: ((Bitmap?) -> Unit)? = null

    var onSurfaceReady: ((SurfaceTexture) -> Unit)? = null

    private val onFrame = SurfaceTexture.OnFrameAvailableListener { view.requestRender() }

    init {
        val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val tex = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertices).position(0)
        texBuffer = ByteBuffer.allocateDirect(tex.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        texBuffer.put(tex).position(0)
        Matrix.setIdentityM(rotationMatrix, 0)
    }

    /** angleDegrees: 预览旋转角；mirror: 前置摄像头水平镜像 */
    fun setTransform(angleDegrees: Int, mirror: Boolean) {
        Matrix.setRotateM(rotationMatrix, 0, angleDegrees.toFloat(), 0f, 0f, 1f)
        if (mirror) {
            val m = FloatArray(16)
            Matrix.setIdentityM(m, 0)
            m[0] = -1f
            val tmp = FloatArray(16)
            Matrix.multiplyMM(tmp, 0, m, 0, rotationMatrix, 0)
            System.arraycopy(tmp, 0, rotationMatrix, 0, 16)
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = createProgram()
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoord = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uTexture = GLES20.glGetUniformLocation(program, "uTexture")
        uBeauty = GLES20.glGetUniformLocation(program, "uBeauty")
        uWhite = GLES20.glGetUniformLocation(program, "uWhite")
        uTexel = GLES20.glGetUniformLocation(program, "uTexel")

        oesTexId = createOESTexture()
        surfaceTexture = SurfaceTexture(oesTexId)
        surfaceTexture!!.setOnFrameAvailableListener(onFrame)
        onSurfaceReady?.invoke(surfaceTexture!!)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val st = surfaceTexture ?: return
        st.updateTexImage()
        st.getTransformMatrix(texMatrix)
        Matrix.multiplyMM(finalMatrix, 0, rotationMatrix, 0, texMatrix, 0)

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexId)
        GLES20.glUniform1i(uTexture, 0)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, finalMatrix, 0)
        GLES20.glUniform1f(uBeauty, beautyLevel)
        GLES20.glUniform1f(uWhite, whiteLevel)
        GLES20.glUniform2f(uTexel, 1f / width.coerceAtLeast(1), 1f / height.coerceAtLeast(1))
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(aTextureCoord)
        GLES20.glVertexAttribPointer(aTextureCoord, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        if (captureRequested) {
            captureRequested = false
            val cb = captureCallback
            captureCallback = null
            cb?.invoke(readFrame())
        }
    }

    /** 直接从默认帧缓冲读回当前（已美颜）画面，垂直翻转后转成 Bitmap */
    private fun readFrame(): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        val src = ByteArray(width * height * 4)
        buf.get(src)
        val flipped = ByteArray(width * height * 4)
        val stride = width * 4
        for (y in 0 until height) {
            System.arraycopy(src, y * stride, flipped, (height - 1 - y) * stride, stride)
        }
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(flipped))
        return bmp
    }

    private fun createOESTexture(): Int {
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, t[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return t[0]
    }

    private fun loadShader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }

    private fun createProgram(): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, Shaders.VERTEX)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, Shaders.FRAGMENT)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        return p
    }
}
