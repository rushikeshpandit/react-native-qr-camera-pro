package com.qrcamerapro

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import android.util.Log

import android.app.Activity
import androidx.core.app.ActivityCompat

// CameraX related imports
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.lang.ref.WeakReference

class QrCameraProModule(
    reactContext: ReactApplicationContext,
) : NativeQrCameraProSpec(reactContext) {
    private val TAG = "QrCameraProModule"
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    private var isInitializing = false
    // Tracks whether startScanning was called but binding hasn't completed yet.
    // Used by setPreviewView to trigger a deferred bind when the view attaches late.
    private var pendingBind = false

    private var lastScannedCode: String? = null
    private var lastScanTime: Long = 0L
    private val scanThrottleInterval: Long = 500L

    private var currentPreviewView: WeakReference<PreviewView>? = null

    init {
        instance = this
    }

    // Called by QrCameraProView.onAttachedToWindow / onDetachedFromWindow
    fun setPreviewView(previewView: PreviewView?) {
        Log.d(TAG, "setPreviewView: ${previewView != null}")
        currentPreviewView = if (previewView != null) WeakReference(previewView) else null

        // FIX 3: Race condition — if startScanning() was called before the view was
        // attached, bindCameraUseCases() would have seen view == null and aborted with
        // a no-op (view?.post is a no-op when view is null). Nothing would ever retry.
        // Now, when the view finally arrives and the provider is ready, we trigger the
        // bind ourselves.
        if (previewView != null && pendingBind && cameraProvider != null && camera == null) {
            val activity = reactApplicationContext.currentActivity
            activity?.runOnUiThread { bindCameraUseCases() }
        }
    }

    override fun getName() = NAME

    override fun startScanning() {
        val activity = reactApplicationContext.currentActivity ?: return

        activity.runOnUiThread {
            Log.d(TAG, "startScanning called on UI thread")
            if (camera != null) {
                Log.d(TAG, "Camera already running, ignoring startScanning")
                return@runOnUiThread
            }

            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                pendingBind = true
                initializeCamera()
            } else {
                Log.d(TAG, "Requesting camera permission")
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.CAMERA),
                    101,
                )
            }
        }
    }

    override fun stopScanning() {
        val activity = reactApplicationContext.currentActivity ?: return
        activity.runOnUiThread {
            Log.d(TAG, "stopScanning called")
            pendingBind = false
            releaseCamera()
        }
    }

    override fun toggleTorch(enabled: Boolean) {
        val activity = reactApplicationContext.currentActivity ?: return
        activity.runOnUiThread {
            camera?.cameraControl?.enableTorch(enabled)
        }
    }

    override fun addListener(eventName: String) {}
    override fun removeListeners(count: Double) {}

    private fun initializeCamera() {
        if (isInitializing) return
        isInitializing = true

        Log.d(TAG, "initializeCamera")
        val activity = reactApplicationContext.currentActivity ?: run {
            isInitializing = false
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera provider", e)
                sendError("Failed to get camera provider: ${e.message}")
            } finally {
                isInitializing = false
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun bindCameraUseCases() {
        // FIX 4: Split the abort conditions so each one has the appropriate response,
        // rather than one catch-all that silently swallows the wrong cases.

        val activity = reactApplicationContext.currentActivity
        if (activity == null || activity !is LifecycleOwner) {
            Log.d(TAG, "bindCameraUseCases: no valid LifecycleOwner activity")
            return
        }
        if (cameraProvider == null) {
            Log.d(TAG, "bindCameraUseCases: provider not ready yet")
            return
        }
        if (camera != null) {
            Log.d(TAG, "bindCameraUseCases: camera already bound, skipping")
            return
        }

        val view = currentPreviewView?.get()
        if (view == null) {
            // View hasn't attached yet. setPreviewView() will call us again when it does.
            Log.d(TAG, "bindCameraUseCases: view not attached yet — will retry via setPreviewView")
            return
        }

        if (view.width == 0 || view.height == 0) {
            // View is attached but hasn't been laid out yet. Post a single retry.
            Log.d(TAG, "bindCameraUseCases: view not laid out yet (${view.width}x${view.height}), posting retry")
            view.post { bindCameraUseCases() }
            return
        }

        Log.d(TAG, "bindCameraUseCases: proceeding — view=${view.width}x${view.height}")

        try {
            cameraProvider?.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                        handleBarcode(barcode)
                    })
                }

            camera = cameraProvider?.bindToLifecycle(
                activity,
                cameraSelector,
                preview,
                imageAnalyzer,
            )

            pendingBind = false
            Log.d(TAG, "Camera bound successfully")
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            sendError("Use case binding failed: ${exc.localizedMessage}")
        }
    }

    private fun releaseCamera() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            Log.d(TAG, "Camera released")
        } catch (e: Exception) {
            Log.e(TAG, "Error during releaseCamera", e)
        }
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        pendingBind = false
        val activity = reactApplicationContext.currentActivity
        activity?.runOnUiThread {
            releaseCamera()
            cameraExecutor.shutdown()
        }
    }

    private fun handleBarcode(barcode: com.google.mlkit.vision.barcode.common.Barcode) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScanTime > scanThrottleInterval) {
            val barcodeData = barcode.rawValue
            if (barcodeData != null && barcodeData != lastScannedCode) {
                val params = Arguments.createMap()
                params.putString("data", barcodeData)
                params.putString("type", getBarcodeFormatName(barcode.format))
                sendEvent("onBarcodeScanned", params)
                lastScannedCode = barcodeData
                lastScanTime = currentTime
            }
        }
    }

    private fun sendError(message: String) {
        val params = Arguments.createMap()
        params.putString("message", message)
        sendEvent("onCameraError", params)
    }

    private class BarcodeAnalyzer(
        private val listener: (com.google.mlkit.vision.barcode.common.Barcode) -> Unit,
    ) : ImageAnalysis.Analyzer {
        private val barcodeScanner = BarcodeScanning.getClient()

        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                barcodeScanner
                    .process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) listener(barcodes[0])
                    }
                    .addOnFailureListener { /* no-op */ }
                    .addOnCompleteListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun getBarcodeFormatName(format: Int): String =
        when (format) {
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE -> "QR_CODE"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8 -> "EAN_8"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13 -> "EAN_13"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417 -> "PDF_417"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC -> "AZTEC"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128 -> "CODE_128"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_39 -> "CODE_39"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_93 -> "CODE_93"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ITF -> "ITF"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E -> "UPC_E"
            else -> "UNKNOWN"
        }

    private fun sendEvent(eventName: String, params: WritableMap?) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    companion object {
        const val NAME = "QrCameraPro"
        var instance: QrCameraProModule? = null
    }
}