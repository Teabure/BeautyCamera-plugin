package com.mytools.beautycamera

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Environment
import android.view.Surface
import android.view.ViewGroup
import android.widget.FrameLayout
import io.dcloud.feature.uniapp.annotation.UniJSMethod
import io.dcloud.feature.uniapp.bridge.UniJSCallback
import io.dcloud.feature.uniapp.common.UniModule
import java.io.File
import java.io.FileOutputStream

class BeautyCameraModule : UniModule() {

    private var glView: GLCameraView? = null
    private var controller: CameraController? = null
    private var activity: Activity? = null
    private var cameraManager: CameraManager? = null

    @UniJSMethod(uiThread = true)
    fun init(options: Map<String, Any>?, callback: UniJSCallback) {
        try {
            val ctx = mUniSDKInstance.context
            activity = ctx as? Activity
            if (activity == null) { callback.invoke(mapOf("code" to -1, "msg" to "activity null")); return }
            cameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            glView = GLCameraView(activity!!)
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            val decor = activity!!.window.decorView as ViewGroup
            decor.addView(glView, 0, lp)
            glView!!.onResume()

            val r = glView!!.renderer
            r.onSurfaceReady = { st ->
                controller = CameraController(cameraManager!!,
                    onReady = {
                        try {
                            val dispDeg = when (activity!!.windowManager.defaultDisplay.rotation) {
                                Surface.ROTATION_90 -> 90
                                Surface.ROTATION_180 -> 180
                                Surface.ROTATION_270 -> 270
                                else -> 0
                            }
                            val chars = cameraManager!!.getCameraCharacteristics(controller!!.currentCameraId!!)
                            val sensor = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                            val front = controller!!.facing == CameraCharacteristics.LENS_FACING_FRONT
                            val angle = if (!front) (sensor - dispDeg + 360) % 360 else (sensor + dispDeg) % 360
                            r.setTransform(angle, front)
                        } catch (_: Exception) { }
                        activity!!.runOnUiThread { callback.invoke(mapOf("code" to 0, "msg" to "ok")) }
                    },
                    onError = { err ->
                        activity!!.runOnUiThread { callback.invoke(mapOf("code" to -1, "msg" to err)) }
                    }
                )
                controller!!.start(st)
            }
        } catch (e: Exception) {
            callback.invoke(mapOf("code" to -1, "msg" to (e.message ?: "init failed")))
        }
    }

    @UniJSMethod(uiThread = true)
    fun capture(options: Map<String, Any>?, callback: UniJSCallback) {
        val r = glView?.renderer
        if (r == null || activity == null) {
            callback.invoke(mapOf("code" to -1, "msg" to "not ready"))
            return
        }
        val act = activity!!
        val dir = File(act.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "camera")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
        r.captureCallback = { bmp: Bitmap? ->
            act.runOnUiThread {
                try {
                    if (bmp == null) { callback.invoke(mapOf("code" to -1, "msg" to "capture null")); return@runOnUiThread }
                    val out = FileOutputStream(file)
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    out.flush(); out.close()
                    bmp.recycle()
                    callback.invoke(mapOf("code" to 0, "path" to file.absolutePath, "uri" to "file://" + file.absolutePath))
                } catch (e: Exception) {
                    callback.invoke(mapOf("code" to -1, "msg" to (e.message ?: "save failed")))
                }
            }
        }
        r.captureRequested = true
    }

    @UniJSMethod(uiThread = true)
    fun setBeauty(options: Map<String, Any>?, callback: UniJSCallback?) {
        val beauty = (options?.get("beauty") as? Number)?.toFloat() ?: 0f
        val white = (options?.get("white") as? Number)?.toFloat() ?: 0f
        val r = glView?.renderer
        r?.beautyLevel = (beauty.coerceIn(0f, 100f)) / 100f
        r?.whiteLevel = (white.coerceIn(0f, 100f)) / 100f
    }

    @UniJSMethod(uiThread = true)
    fun focus(options: Map<String, Any>?, callback: UniJSCallback?) {
        val x = (options?.get("x") as? Number)?.toFloat() ?: 0.5f
        val y = (options?.get("y") as? Number)?.toFloat() ?: 0.5f
        controller?.focusAt(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }

    @UniJSMethod(uiThread = true)
    fun setZoom(options: Map<String, Any>?, callback: UniJSCallback?) {
        val z = (options?.get("zoom") as? Number)?.toFloat() ?: 1f
        controller?.setZoom(z)
    }

    @UniJSMethod(uiThread = true)
    fun setExposure(options: Map<String, Any>?, callback: UniJSCallback?) {
        val ev = (options?.get("ev") as? Number)?.toInt() ?: 0
        controller?.setExposureComp(ev)
    }

    @UniJSMethod(uiThread = true)
    fun setWhiteBalance(options: Map<String, Any>?, callback: UniJSCallback?) {
        val mode = (options?.get("mode") as? String) ?: "auto"
        controller?.setWhiteBalance(mode)
    }

    @UniJSMethod(uiThread = true)
    fun setFlash(options: Map<String, Any>?, callback: UniJSCallback?) {
        val mode = (options?.get("mode") as? String) ?: "off"
        controller?.setFlashMode(mode)
    }

    @UniJSMethod(uiThread = true)
    fun getInfo(options: Map<String, Any>?, callback: UniJSCallback?) {
        callback?.invoke(mapOf(
            "code" to 0,
            "maxZoom" to (controller?.maxZoom ?: 1f),
            "facing" to (if (controller?.facing == CameraCharacteristics.LENS_FACING_FRONT) "front" else "back")
        ))
    }

    @UniJSMethod(uiThread = true)
    fun switchCamera(options: Map<String, Any>?, callback: UniJSCallback?) {
        glView?.renderer?.surfaceTexture ?: return
        controller?.switchCamera()
    }

    @UniJSMethod(uiThread = true)
    fun release(options: Map<String, Any>?, callback: UniJSCallback?) {
        try {
            controller?.stop()
            val decor = activity?.window?.decorView as? ViewGroup
            glView?.let { decor?.removeView(it) }
            glView?.onPause()
            glView = null
            controller = null
        } catch (_: Exception) { }
    }
}
