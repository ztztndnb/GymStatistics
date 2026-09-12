package com.gymstatistics.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

internal data class CaptureCropRect(val left: Int, val top: Int, val width: Int, val height: Int)

internal fun calculateCaptureCropRect(
    rawWidth: Int,
    rawHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
): CaptureCropRect {
    if (rawWidth <= 0 || rawHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
        return CaptureCropRect(0, 0, rawWidth.coerceAtLeast(0), rawHeight.coerceAtLeast(0))
    }
    val viewAspect = viewWidth.toFloat() / viewHeight
    val orientationsDiffer = (viewWidth > viewHeight) != (rawWidth > rawHeight)
    val targetRawAspect = if (orientationsDiffer) 1f / viewAspect else viewAspect
    val rawAspect = rawWidth.toFloat() / rawHeight
    return if (rawAspect > targetRawAspect) {
        val cropWidth = (rawHeight * targetRawAspect).roundToInt().coerceIn(1, rawWidth)
        CaptureCropRect((rawWidth - cropWidth) / 2, 0, cropWidth, rawHeight)
    } else {
        val cropHeight = (rawWidth / targetRawAspect).roundToInt().coerceIn(1, rawHeight)
        CaptureCropRect(0, (rawHeight - cropHeight) / 2, rawWidth, cropHeight)
    }
}

internal data class PreviewScale(val x: Float, val y: Float)

internal fun calculatePreviewScale(
    viewWidth: Int,
    viewHeight: Int,
    bufferWidth: Int,
    bufferHeight: Int,
): PreviewScale {
    val viewAspect = viewWidth.toFloat() / viewHeight
    val orientationsDiffer = (viewWidth > viewHeight) != (bufferWidth > bufferHeight)
    val displayedBufferWidth = if (orientationsDiffer) bufferHeight else bufferWidth
    val displayedBufferHeight = if (orientationsDiffer) bufferWidth else bufferHeight
    val bufferAspect = displayedBufferWidth.toFloat() / displayedBufferHeight
    return if (bufferAspect > viewAspect) {
        PreviewScale(x = bufferAspect / viewAspect, y = 1f)
    } else {
        PreviewScale(x = 1f, y = viewAspect / bufferAspect)
    }
}

class FoodCameraView(context: Context) : FrameLayout(context) {
    private val cameraLock = Any()
    private val textureView = TextureView(context)
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var pendingFile: File? = null
    private var pendingSaved: ((File) -> Unit)? = null
    private var pendingError: ((String) -> Unit)? = null
    private var pendingCapture: File? = null
    private var captureSubmitted = false
    @Volatile private var previewBufferSize: Size? = null
    @Volatile private var previewViewWidth = 0
    @Volatile private var previewViewHeight = 0
    @Volatile private var started = false
    @Volatile private var activeSurfaceTexture: SurfaceTexture? = null
    private var surfaceGeneration = 0L
    private var openingCamera = false

    init {
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                val generation = synchronized(cameraLock) {
                    if (activeSurfaceTexture !== surface) {
                        activeSurfaceTexture = surface
                        surfaceGeneration += 1
                    }
                    surfaceGeneration
                }
                if (started) {
                    cameraHandler?.post { openCamera(surface, generation) }
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                synchronized(cameraLock) {
                    if (activeSurfaceTexture === surface) {
                        activeSurfaceTexture = null
                        surfaceGeneration += 1
                    }
                }
                val handler = cameraHandler
                if (handler != null) {
                    handler.post {
                        synchronized(cameraLock) {
                            closeCamera()
                        }
                        reopenCurrentSurface()
                    }
                } else {
                    synchronized(cameraLock) {
                        closeCamera()
                    }
                }
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        previewViewWidth = width
        previewViewHeight = height
        previewBufferSize?.let { size ->
            textureView.post { applyPreviewTransform(size) }
        }
    }

    fun start() {
        synchronized(cameraLock) {
            if (started) return
            started = true
            openingCamera = false
            cameraThread = HandlerThread("food-camera").also { it.start() }
            cameraHandler = Handler(cameraThread!!.looper)
        }

        val surface = textureView.surfaceTexture
        if (textureView.isAvailable && surface != null) {
            val generation = synchronized(cameraLock) {
                if (activeSurfaceTexture !== surface) {
                    activeSurfaceTexture = surface
                    surfaceGeneration += 1
                }
                surfaceGeneration
            }
            cameraHandler?.post { openCamera(surface, generation) }
        }
    }

    fun stop() {
        val handler: Handler?
        val thread: HandlerThread?
        synchronized(cameraLock) {
            started = false
            activeSurfaceTexture = null
            surfaceGeneration += 1
            openingCamera = false
            pendingFile?.delete()
            pendingFile = null
            pendingSaved = null
            pendingError = null
            pendingCapture = null
            captureSubmitted = false
            handler = cameraHandler
            thread = cameraThread
            cameraHandler = null
            cameraThread = null
        }
        handler?.post {
            synchronized(cameraLock) {
                closeCamera()
            }
        }
        thread?.quitSafely()
    }

    fun takePicture(file: File, onSaved: (File) -> Unit, onError: (String) -> Unit) {
        synchronized(cameraLock) {
            val session = captureSession
            val device = cameraDevice
            val reader = imageReader
            if (!started) {
                onError("相机尚未准备好，请稍后再试")
                return
            }
            if (pendingFile != null) {
                onError("正在拍摄，请稍后再试")
                return
            }
            pendingFile = file
            pendingSaved = onSaved
            pendingError = onError
            pendingCapture = file
            if (!submitPendingCapture()) {
                schedulePendingCaptureTimeout(file)
                reopenCurrentSurface()
            }
        }
    }

    private fun submitPendingCapture(): Boolean {
        val file = pendingCapture ?: return false
        val session = captureSession ?: return false
        val device = cameraDevice ?: return false
        val reader = imageReader ?: return false
        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                cameraCharacteristics?.let { characteristics ->
                    set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation(characteristics))
                }
            }.build()
            session.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure,
                ) {
                    notifyCaptureError("相机没有完成拍摄")
                }

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) = Unit
            }, cameraHandler)
            pendingCapture = null
            captureSubmitted = true
            return true
        } catch (exception: CameraAccessException) {
            notifyCaptureError(exception.message ?: "无法访问相机")
        } catch (exception: IllegalArgumentException) {
            notifyCaptureError(exception.message ?: "相机暂时不可用")
        }
        return false
    }

    private fun schedulePendingCaptureTimeout(file: File) {
        cameraHandler?.postDelayed({
            val callback: ((String) -> Unit)?
            synchronized(cameraLock) {
                if (pendingCapture !== file || captureSubmitted) return@postDelayed
                callback = pendingError
                pendingCapture = null
                pendingFile = null
                pendingSaved = null
                pendingError = null
            }
            file.delete()
            post { callback?.invoke("相机尚未准备好，请稍后再试") }
        }, 1_500L)
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(surface: SurfaceTexture, generation: Long) {
        val handler = cameraHandler ?: return
        synchronized(cameraLock) {
            if (!started || activeSurfaceTexture !== surface || !textureView.isAvailable) return
            if (generation != surfaceGeneration || openingCamera || cameraDevice != null) return
            openingCamera = true
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            synchronized(cameraLock) {
                openingCamera = false
            }
            return
        }

        try {
            val cameraId = findBackCamera() ?: run {
                synchronized(cameraLock) {
                    openingCamera = false
                }
                notifyCaptureError("未找到可用相机")
                return
            }
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSize = map?.getOutputSizes(ImageFormat.JPEG)
                ?.maxByOrNull { it.width.toLong() * it.height }
                ?: Size(1920, 1080)
            val reader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener({ availableReader ->
                    val image = availableReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val pending = synchronized(cameraLock) {
                        val result = Triple(pendingFile, pendingSaved, pendingError)
                        pendingFile = null
                        pendingSaved = null
                        pendingError = null
                        pendingCapture = null
                        captureSubmitted = false
                        result
                    }
                    val file = pending.first
                    val saved = pending.second
                    val error = pending.third
                    var imageClosed = false
                    try {
                        if (file == null || saved == null) {
                            image.close()
                            imageClosed = true
                            return@setOnImageAvailableListener
                        }
                        val buffer = image.planes.first().buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        image.close()
                        imageClosed = true
                        FileOutputStream(file).use { it.write(bytes) }
                        check(cropPhotoToPreview(file, previewViewWidth, previewViewHeight)) {
                            "无法裁剪拍摄照片"
                        }
                        val savedFile = file
                        val savedCallback = saved
                        post { savedCallback(savedFile) }
                    } catch (exception: Exception) {
                        if (!imageClosed) image.close()
                        file?.delete()
                        post { error?.invoke(exception.message ?: "无法保存照片") }
                    }
                }, handler)
            }
            synchronized(cameraLock) {
                if (!started || activeSurfaceTexture !== surface || !textureView.isAvailable) {
                    reader.close()
                    openingCamera = false
                    return
                }
                if (generation != surfaceGeneration) {
                    reader.close()
                    openingCamera = false
                    return
                }
                cameraCharacteristics = characteristics
                imageReader?.close()
                imageReader = reader
            }
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    var valid = false
                    var shouldReopen = false
                    synchronized(cameraLock) {
                        openingCamera = false
                        if (!started || activeSurfaceTexture !== surface || !textureView.isAvailable) {
                            closeCamera()
                        } else if (generation != surfaceGeneration) {
                            closeCamera()
                        } else {
                            cameraDevice = camera
                            valid = true
                        }
                        shouldReopen = started && activeSurfaceTexture != null && textureView.isAvailable && !valid
                    }
                    if (!valid) {
                        camera.close()
                        if (shouldReopen) reopenCurrentSurface()
                        return
                    }
                    startPreview(surface, generation)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    synchronized(cameraLock) {
                        if (cameraDevice === camera) {
                            cameraDevice = null
                        }
                        openingCamera = false
                        captureSession?.close()
                        captureSession = null
                        imageReader?.close()
                        imageReader = null
                        previewSurface?.release()
                        previewSurface = null
                    }
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    synchronized(cameraLock) {
                        if (cameraDevice === camera) {
                            cameraDevice = null
                        }
                        openingCamera = false
                        captureSession?.close()
                        captureSession = null
                        imageReader?.close()
                        imageReader = null
                        previewSurface?.release()
                        previewSurface = null
                    }
                    camera.close()
                    notifyCaptureError("相机启动失败")
                }
            }, handler)
        } catch (exception: CameraAccessException) {
            synchronized(cameraLock) {
                openingCamera = false
                closeCamera()
            }
            notifyCaptureError(exception.message ?: "无法启动相机")
        } catch (exception: IllegalArgumentException) {
            synchronized(cameraLock) {
                openingCamera = false
                closeCamera()
            }
            notifyCaptureError(exception.message ?: "无法启动相机")
        }
    }

    private fun startPreview(surface: SurfaceTexture, generation: Long) {
        synchronized(cameraLock) {
            if (!started || activeSurfaceTexture !== surface || !textureView.isAvailable) {
                closeCamera()
                return
            }
            if (generation != surfaceGeneration) {
                closeCamera()
                return
            }
            val device = cameraDevice ?: return
            val reader = imageReader ?: return
            val size = cameraCharacteristics
                ?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(SurfaceTexture::class.java)
                ?.maxByOrNull { it.width.toLong() * it.height }
                ?: Size(1920, 1080)
            try {
                surface.setDefaultBufferSize(size.width, size.height)
                previewBufferSize = size
                textureView.post { applyPreviewTransform(size) }
                val outputSurface = Surface(surface)
                previewSurface = outputSurface
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(outputSurface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }.build()
                device.createCaptureSession(
                    listOf(outputSurface, reader.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            synchronized(cameraLock) {
                                if (!started || activeSurfaceTexture !== surface || !textureView.isAvailable) {
                                    session.close()
                                    return
                                }
                                if (generation != surfaceGeneration) {
                                    session.close()
                                    return
                                }
                                try {
                                    captureSession = session
                                    session.setRepeatingRequest(request, null, cameraHandler)
                                    submitPendingCapture()
                                } catch (exception: CameraAccessException) {
                                    closeCamera()
                                    notifyCaptureError(exception.message ?: "无法启动相机预览")
                                } catch (exception: IllegalArgumentException) {
                                    closeCamera()
                                    notifyCaptureError(exception.message ?: "无法启动相机预览")
                                }
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            synchronized(cameraLock) {
                                session.close()
                                if (captureSession === session) {
                                    captureSession = null
                                }
                                notifyCaptureError("相机预览启动失败")
                            }
                        }
                    },
                    cameraHandler,
                )
            } catch (exception: CameraAccessException) {
                closeCamera()
                notifyCaptureError(exception.message ?: "无法启动相机预览")
            } catch (exception: IllegalArgumentException) {
                closeCamera()
                notifyCaptureError(exception.message ?: "无法启动相机预览")
            }
        }
    }

    private fun reopenCurrentSurface() {
        val handler = cameraHandler ?: return
        val surface: SurfaceTexture
        val generation: Long
        synchronized(cameraLock) {
            if (!started || activeSurfaceTexture == null || !textureView.isAvailable) return
            surface = activeSurfaceTexture ?: return
            generation = surfaceGeneration
        }
        handler.post { openCamera(surface, generation) }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        previewSurface?.release()
        previewSurface = null
        cameraCharacteristics = null
        previewBufferSize = null
    }

    private fun applyPreviewTransform(bufferSize: Size) {
        val viewWidth = textureView.width
        val viewHeight = textureView.height
        if (viewWidth <= 0 || viewHeight <= 0 || bufferSize.width <= 0 || bufferSize.height <= 0) return
        val scale = calculatePreviewScale(viewWidth, viewHeight, bufferSize.width, bufferSize.height)
        val transform = Matrix().apply {
            setScale(scale.x, scale.y, viewWidth / 2f, viewHeight / 2f)
        }
        textureView.setTransform(transform)
    }

    private fun cropPhotoToPreview(file: File, viewWidth: Int, viewHeight: Int): Boolean {
        if (viewWidth <= 0 || viewHeight <= 0) return true
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return false
        val crop = calculateCaptureCropRect(bitmap.width, bitmap.height, viewWidth, viewHeight)
        if (crop.left == 0 && crop.top == 0 && crop.width == bitmap.width && crop.height == bitmap.height) {
            bitmap.recycle()
            return true
        }
        val cropped = try {
            Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width, crop.height)
        } catch (_: IllegalArgumentException) {
            bitmap.recycle()
            return false
        }
        val croppedFile = File(file.parentFile, "${file.name}.cropped")
        return try {
            FileOutputStream(croppedFile).use { output ->
                check(cropped.compress(Bitmap.CompressFormat.JPEG, 95, output))
            }
            croppedFile.copyTo(file, overwrite = true)
            true
        } catch (_: Exception) {
            false
        } finally {
            croppedFile.delete()
            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()
        }
    }

    private fun findBackCamera(): String? = cameraManager.cameraIdList.firstOrNull { id ->
        cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
    }

    private fun notifyCaptureError(message: String) {
        val error = synchronized(cameraLock) {
            val callback = pendingError
            pendingFile?.delete()
            pendingFile = null
            pendingSaved = null
            pendingError = null
            pendingCapture = null
            captureSubmitted = false
            callback
        }
        post { error?.invoke(message) }
    }

    private fun jpegOrientation(characteristics: CameraCharacteristics): Int {
        val rotation = (context as? android.app.Activity)?.windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        val degrees = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        return (sensor - degrees + 360) % 360
    }
}
