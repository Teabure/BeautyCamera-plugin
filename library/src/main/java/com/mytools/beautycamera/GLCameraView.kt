package com.mytools.beautycamera

import android.content.Context
import android.opengl.GLSurfaceView

/**
 * 承载相机预览的 GLSurfaceView，renderer 持有 OES 纹理与美颜逻辑。
 */
class GLCameraView(context: Context) : GLSurfaceView(context) {
    val renderer: CameraRenderer

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        renderer = CameraRenderer(this)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
