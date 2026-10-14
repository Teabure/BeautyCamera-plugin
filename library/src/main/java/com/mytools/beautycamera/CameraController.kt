package com.mytools.beautycamera

import android.annotation.SuppressLint
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface

/**
 * 封装 Camera2：打开/预览/前后摄切换/闪光/对焦/变焦/曝光/白平衡。
 * 渲染目标复用外部传入的 SurfaceTexture。
 */
class CameraController(
    private val cameraManager: CameraManager,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit
) {
    var cameraDevice: CameraDevice? = null
    var captureSession: CameraCaptureSession? = null
    var facing = CameraCharacteristics.LENS_FACING_BACK
        private set
    var flashMode = "off"
        private set
    var currentCameraId: String? = null
        private set
    var zoomRatio = 1f
        private set
    var exposureComp = 0
        private set
    var wbMode = CameraCharacteristics.CONTROL_AWB_MODE_AUTO
        private set
    var maxZoom = 1f
        private set

    private var surfaceTexture: SurfaceTexture? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var activeArray: Rect? = null

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            cameraDevice = device
            createSession()
        }
        override fun onDisconnected(device: CameraDevice) { device.close(); cameraDevice = null }
        override fun onError(device: CameraDevice, error: Int) {
            device.close(); cameraDevice = null; onError("open error $error")
        }
    }

    fun start(surfaceTexture: SurfaceTexture) {
        this.surfaceTexture = surfaceTexture
        startBackground()
        openCamera()
    }

    private fun startBackground() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val camId = chooseCameraId(facing) ?: run { onError("no camera"); return }
        currentCameraId = camId
        try {
            cameraManager.openCamera(camId, stateCallback, backgroundHandler)
        } catch (e: Exception) {
            onError(e.message ?: "openCamera exception")
        }
    }

    private fun chooseCameraId(target: Int): String? {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val f = chars.get(CameraCharacteristics.LENS_FACING) ?: continue
            if (f == target) return id
        }
        return cameraManager.cameraIdList.firstOrNull()
    }

    private fun createSession() {
        val device = cameraDevice ?: return
        val st = surfaceTexture ?: return
        val surface = Surface(st)
        device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                try {
                    val chars = cameraManager.getCameraCharacteristics(currentCameraId!!)
                    activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    val mz = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                    maxZoom = mz ?: 1f
                    startRepeating()
                    onReady()
                } catch (e: Exception) {
                    onError(e.message ?: "session error")
                }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                onError("configure failed")
            }
        }, backgroundHandler)
    }

    private fun startRepeating() {
        val device = cameraDevice ?: return
        val st = surfaceTexture ?: return
        try {
            val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            req.addTarget(Surface(st))
            applyPreview(req)
            captureSession?.setRepeatingRequest(req.build(), null, backgroundHandler)
        } catch (e: Exception) {
            onError(e.message ?: "repeating error")
        }
    }

    private fun applyPreview(req: CaptureRequest.Builder) {
        when (flashMode) {
            "on", "torch" -> req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            "auto" -> req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
            else -> req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }
        val rect = activeArray
        if (zoomRatio > 1f && rect != null) {
            val halfW = (rect.width() / (2f * zoomRatio)).toInt().coerceAtLeast(1)
            val halfH = (rect.height() / (2f * zoomRatio)).toInt().coerceAtLeast(1)
            val cx = rect.centerX(); val cy = rect.centerY()
            val crop = Rect(
                (cx - halfW).coerceIn(rect.left, rect.right),
                (cy - halfH).coerceIn(rect.top, rect.bottom),
                (cx + halfW).coerceIn(rect.left, rect.right),
                (cy + halfH).coerceIn(rect.top, rect.bottom)
            )
            req.set(CaptureRequest.SCALER_CROP_REGION, crop)
        }
        req.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureComp)
        req.set(CaptureRequest.CONTROL_AWB_MODE, wbMode)
        req.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
    }

    fun setZoom(ratio: Float) {
        zoomRatio = ratio.coerceIn(1f, maxZoom)
        startRepeating()
    }

    fun setExposureComp(ev: Int) {
        exposureComp = ev
        startRepeating()
    }

    fun setWhiteBalance(mode: String) {
        wbMode = when (mode) {
            "daylight" -> CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT
            "cloudy" -> CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
            "fluorescent" -> CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT
            "incandescent" -> CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT
            "auto" -> CameraCharacteristics.CONTROL_AWB_MODE_AUTO
            else -> CameraCharacteristics.CONTROL_AWB_MODE_AUTO
        }
        startRepeating()
    }

    fun setFlashMode(mode: String) {
        flashMode = mode
        startRepeating()
    }

    /** 对预览做单次对焦 + 测光；nx,ny 为归一化坐标(0..1) */
    fun focusAt(nx: Float, ny: Float) {
        val device = cameraDevice ?: return
        val st = surfaceTexture ?: return
        val rect = activeArray ?: return
        val cx = (nx * rect.width()).toInt().coerceIn(rect.left, rect.right)
        val cy = (ny * rect.height()).toInt().coerceIn(rect.top, rect.bottom)
        val s = (rect.width() * 0.08f).toInt().coerceAtLeast(40)
        val left = (cx - s).coerceIn(rect.left, rect.right)
        val top = (cy - s).coerceIn(rect.top, rect.bottom)
        val right = (cx + s).coerceIn(rect.left, rect.right)
        val bottom = (cy + s).coerceIn(rect.top, rect.bottom)
        val focusRect = Rect(left, top, right, bottom)
        try {
            val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            req.addTarget(Surface(st))
            applyPreview(req)
            req.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            req.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            req.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            captureSession?.capture(req.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    startRepeating()
                }
            }, backgroundHandler)
        } catch (e: Exception) { }
    }

    fun switchCamera() {
        try { captureSession?.close() } catch (_: Exception) {}
        try { cameraDevice?.close() } catch (_: Exception) {}
        captureSession = null
        cameraDevice = null
        facing = if (facing == CameraCharacteristics.LENS_FACING_BACK)
            CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        zoomRatio = 1f
        openCamera()
    }

    fun stop() {
        try { captureSession?.close() } catch (_: Exception) {}
        try { cameraDevice?.close() } catch (_: Exception) {}
        captureSession = null
        cameraDevice = null
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }
}
