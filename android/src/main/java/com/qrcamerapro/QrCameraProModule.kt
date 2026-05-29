package com.qrcamerapro

import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * React Native TurboModule that drives the QR/Barcode camera scanner on Android.
 *
 * Responsibilities (SRP):
 *  - Manage the CameraX session lifecycle (init, bind, release).
 *  - Forward JS method calls (startScanning, stopScanning, toggleTorch) to CameraX.
 *  - Emit scan results and errors back to JavaScript via [DeviceEventManagerModule].
 *
 * Delegated to separate classes (DRY / SRP):
 *  - Barcode throttle + dedup → [BarcodeThrottler]
 *  - Format int → JS string mapping → [BarcodeFormatMapper]
 *  - ML Kit image analysis → [BarcodeAnalyzer]
 *
 * @param reactContext The application-scoped React context provided by the RN bridge.
 */
internal class QrCameraProModule(
    reactContext: ReactApplicationContext,
) : NativeQrCameraProSpec(reactContext) {

    // -------------------------------------------------------------------------
    // Companion / constants
    // -------------------------------------------------------------------------

    companion object {
        /** Module name registered with the React Native bridge. */
        const val NAME = "QrCameraPro"

        private const val TAG = "QrCameraProModule"

        /** Request code used when asking the user for CAMERA permission. */
        private const val CAMERA_PERMISSION_REQUEST_CODE = 101

        /**
         * Singleton reference to the most recently created module instance.
         *
         * Used by [QrCameraProView] to push the [PreviewView] into the module when
         * the native view attaches to the window. This pattern is necessary because
         * the RN bridge creates modules and views through separate factory paths that
         * have no direct reference to each other.
         *
         * NOTE: Always assigned in [init] and cleared in [onCatalystInstanceDestroy],
         * so this is only non-null while the module is alive.
         */
        @Volatile
        var instance: QrCameraProModule? = null
            private set

        // Event name constants — single source of truth, mirrors the JS layer.
        // Kept here (not in a separate companion) because Kotlin only allows one
        // companion object per class.
        const val EVENT_BARCODE_SCANNED = "onBarcodeScanned"
        const val EVENT_CAMERA_ERROR = "onCameraError"
    }

    // -------------------------------------------------------------------------
    // CameraX state
    // -------------------------------------------------------------------------

    /** The CameraX provider, obtained asynchronously during [initializeCamera]. */
    private var cameraProvider: ProcessCameraProvider? = null

    /** CameraX [Preview] use case; supplies frames to [PreviewView]. */
    private var preview: Preview? = null

    /** CameraX [ImageAnalysis] use case; supplies frames to [BarcodeAnalyzer]. */
    private var imageAnalyzer: ImageAnalysis? = null

    /** Active analyzer instance; kept so it can be [BarcodeAnalyzer.close]d on release. */
    private var barcodeAnalyzer: BarcodeAnalyzer? = null

    /** Bound camera reference; non-null only while the camera is running. */
    private var camera: Camera? = null

    /**
     * Single-thread executor dedicated to image analysis.
     *
     * Re-created lazily via [requireCameraExecutor] whenever the previous executor
     * has been shut down, so that [startScanning] after [stopScanning] always has a
     * live executor to submit work to.
     */
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // -------------------------------------------------------------------------
    // Binding state
    // -------------------------------------------------------------------------

    /**
     * Guards [initializeCamera] against re-entrant calls while the
     * [ProcessCameraProvider] future is still resolving.
     */
    private var isInitializing = false

    /**
     * Set to `true` when [startScanning] has been called but [bindCameraUseCases]
     * has not yet completed (e.g. the [PreviewView] hasn't attached yet, or the
     * camera provider future is still resolving).
     *
     * [setPreviewView] checks this flag so it can trigger a deferred bind as soon
     * as the view becomes available — closing the race condition where startScanning
     * fires before the RN component mounts its native view.
     */
    private var pendingBind = false

    // -------------------------------------------------------------------------
    // Preview view
    // -------------------------------------------------------------------------

    /**
     * Weak reference to the [PreviewView] currently hosted by [QrCameraProView].
     *
     * A [WeakReference] is used so the module does not prevent the view from being
     * garbage-collected after it detaches from the window.
     */
    private var currentPreviewView: WeakReference<PreviewView>? = null

    // -------------------------------------------------------------------------
    // Barcode throttling
    // -------------------------------------------------------------------------

    /** Handles per-session deduplication and time-based throttling of scan events. */
    private val barcodeThrottler = BarcodeThrottler(intervalMs = 500L)

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    init {
        // Publish this instance so QrCameraProView can reach it when it attaches.
        instance = this
    }

    // -------------------------------------------------------------------------
    // NativeQrCameraProSpec — JS-callable methods
    // -------------------------------------------------------------------------

    /** Returns the module name registered with the React Native bridge. */
    override fun getName(): String = NAME

    /**
     * Starts the barcode scanner.
     *
     * Checks camera permission first. If granted, initialises CameraX and binds
     * the use cases. If not granted, requests the permission from the user;
     * the app is responsible for calling [startScanning] again after the user grants it.
     *
     * Must be called from JavaScript. All camera work is dispatched to the UI thread
     * (required by CameraX lifecycle binding) and then to the camera executor.
     */
    override fun startScanning() {
        val activity = reactApplicationContext.currentActivity ?: run {
            Log.w(TAG, "startScanning: no current activity")
            return
        }

        activity.runOnUiThread {
            Log.d(TAG, "startScanning called on UI thread")

            if (camera != null) {
                Log.d(TAG, "startScanning: camera already running, ignoring")
                return@runOnUiThread
            }

            if (hasCameraPermission(activity)) {
                pendingBind = true
                initializeCamera()
            } else {
                Log.d(TAG, "startScanning: requesting CAMERA permission")
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST_CODE,
                )
            }
        }
    }

    /**
     * Stops the barcode scanner and releases all camera resources.
     *
     * Resets [pendingBind] so a stale bind attempt cannot restart the camera after
     * the user has explicitly stopped it. Also resets the [barcodeThrottler] so
     * stale dedup state does not carry into the next session.
     */
    override fun stopScanning() {
        val activity = reactApplicationContext.currentActivity ?: return
        activity.runOnUiThread {
            Log.d(TAG, "stopScanning called")
            pendingBind = false
            releaseCamera()
        }
    }

    /**
     * Turns the device torch on or off.
     *
     * Silently ignored when no camera is bound (e.g. called before [startScanning]).
     *
     * @param enabled `true` to turn the torch on; `false` to turn it off.
     */
    override fun toggleTorch(enabled: Boolean) {
        val activity = reactApplicationContext.currentActivity ?: return
        activity.runOnUiThread {
            camera?.cameraControl?.enableTorch(enabled)
        }
    }

    /**
     * Required by [NativeQrCameraProSpec] / RCTEventEmitter contract.
     * Listener accounting is handled by the RN bridge; no action needed here.
     */
    override fun addListener(eventName: String) { /* managed by RN bridge */ }

    /**
     * Required by [NativeQrCameraProSpec] / RCTEventEmitter contract.
     * Listener accounting is handled by the RN bridge; no action needed here.
     */
    override fun removeListeners(count: Double) { /* managed by RN bridge */ }

    // -------------------------------------------------------------------------
    // Preview view registration (called by QrCameraProView)
    // -------------------------------------------------------------------------

    /**
     * Registers or clears the [PreviewView] that the camera preview renders into.
     *
     * Called by [QrCameraProView.onAttachedToWindow] (with a non-null view) and
     * [QrCameraProView.onDetachedFromWindow] (with `null`).
     *
     * When a view arrives and [pendingBind] is set — meaning [startScanning] was
     * called before the view attached — this method triggers [bindCameraUseCases]
     * on the UI thread to complete the deferred bind.
     *
     * @param previewView The newly attached [PreviewView], or `null` on detach.
     */
    fun setPreviewView(previewView: PreviewView?) {
        Log.d(TAG, "setPreviewView: ${if (previewView != null) "attached" else "detached"}")
        currentPreviewView = previewView?.let { WeakReference(it) }

        // Complete a deferred bind: startScanning() was called before the view attached.
        if (previewView != null && pendingBind && cameraProvider != null && camera == null) {
            reactApplicationContext.currentActivity?.runOnUiThread {
                bindCameraUseCases()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Module lifecycle
    // -------------------------------------------------------------------------

    /**
     * Called by the RN bridge when the JS bundle is about to be destroyed
     * (e.g. on reload or app exit). Releases all camera and executor resources.
     */
    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        pendingBind = false
        instance = null
        reactApplicationContext.currentActivity?.runOnUiThread {
            releaseCamera()
            cameraExecutor.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // Private — camera initialisation
    // -------------------------------------------------------------------------

    /**
     * Asynchronously obtains a [ProcessCameraProvider] from the system and, on
     * success, calls [bindCameraUseCases].
     *
     * Guards against concurrent calls with [isInitializing]. If the provider
     * future fails, an `onCameraError` event is emitted to JS.
     */
    private fun initializeCamera() {
        if (isInitializing) return
        isInitializing = true

        Log.d(TAG, "initializeCamera")
        val activity = reactApplicationContext.currentActivity ?: run {
            isInitializing = false
            return
        }

        ProcessCameraProvider.getInstance(activity).addListener(
            {
                try {
                    cameraProvider = ProcessCameraProvider.getInstance(activity).get()
                    bindCameraUseCases()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get camera provider", e)
                    sendError("Failed to get camera provider: ${e.message}")
                } finally {
                    isInitializing = false
                }
            },
            ContextCompat.getMainExecutor(activity),
        )
    }

    /**
     * Binds CameraX use cases ([Preview] + [ImageAnalysis]) to the activity lifecycle.
     *
     * Performs a series of pre-condition checks before binding — each condition has
     * its own log message so failures are easy to diagnose without a debugger:
     *  - Activity must be a [LifecycleOwner].
     *  - [cameraProvider] must be ready.
     *  - Camera must not already be bound.
     *  - [PreviewView] must be attached and laid out (non-zero size).
     *
     * If the view is attached but not yet laid out, a single retry is posted via
     * [android.view.View.post] and will run after the next layout pass.
     *
     * Must be called on the UI thread.
     */
    private fun bindCameraUseCases() {
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
            // View hasn't attached yet; setPreviewView() will retry when it does.
            Log.d(TAG, "bindCameraUseCases: view not attached yet — will retry via setPreviewView")
            return
        }

        if (view.width == 0 || view.height == 0) {
            // View attached but not yet laid out — post a single retry.
            Log.d(TAG, "bindCameraUseCases: view not laid out yet (${view.width}x${view.height}), retrying after layout")
            view.post { bindCameraUseCases() }
            return
        }

        Log.d(TAG, "bindCameraUseCases: proceeding — view=${view.width}x${view.height}")

        try {
            // Clear any previously bound use cases before re-binding.
            cameraProvider?.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            preview = Preview.Builder().build().also { p ->
                p.setSurfaceProvider(view.surfaceProvider)
            }

            val analyzer = BarcodeAnalyzer { barcode ->
                handleBarcode(barcode)
            }
            barcodeAnalyzer = analyzer

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(requireCameraExecutor(), analyzer) }

            camera = cameraProvider?.bindToLifecycle(
                activity,
                cameraSelector,
                preview,
                imageAnalyzer,
            )

            pendingBind = false
            Log.d(TAG, "bindCameraUseCases: camera bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "bindCameraUseCases: use case binding failed", e)
            sendError("Use case binding failed: ${e.localizedMessage}")
        }
    }

    /**
     * Stops and releases all CameraX resources and the [BarcodeAnalyzer].
     *
     * Resets [barcodeThrottler] so no stale dedup state persists into the next
     * scanning session. Safe to call when no camera is bound.
     *
     * Must be called on the UI thread.
     */
    private fun releaseCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "releaseCamera: error during unbindAll", e)
        }

        // Close the ML Kit scanner client to free native resources.
        barcodeAnalyzer?.close()
        barcodeAnalyzer = null

        camera = null
        imageAnalyzer = null
        preview = null

        // Reset throttler so the next session starts with a clean slate.
        barcodeThrottler.reset()

        Log.d(TAG, "releaseCamera: camera released")
    }

    // -------------------------------------------------------------------------
    // Private — barcode handling
    // -------------------------------------------------------------------------

    /**
     * Processes a detected [Barcode] from [BarcodeAnalyzer].
     *
     * Delegates throttle/dedup to [BarcodeThrottler] and format mapping to
     * [BarcodeFormatMapper]. Only non-null raw values that pass the throttle
     * gate are forwarded to JS via [sendEvent].
     *
     * Called on the camera executor thread.
     *
     * @param barcode The barcode detected by ML Kit.
     */
    private fun handleBarcode(barcode: com.google.mlkit.vision.barcode.common.Barcode) {
        val barcodeValue = barcode.rawValue ?: return

        if (!barcodeThrottler.shouldEmit(barcodeValue)) return

        val params = Arguments.createMap().apply {
            putString("data", barcodeValue)
            putString("type", BarcodeFormatMapper.toJsString(barcode.format))
        }
        sendEvent(EVENT_BARCODE_SCANNED, params)
    }

    // -------------------------------------------------------------------------
    // Private — event emission
    // -------------------------------------------------------------------------

    /**
     * Emits an `onCameraError` event to JavaScript with a descriptive message.
     *
     * @param message A human-readable description of the error condition.
     */
    private fun sendError(message: String) {
        val params = Arguments.createMap().apply {
            putString("message", message)
        }
        sendEvent(EVENT_CAMERA_ERROR, params)
    }

    /**
     * Emits a named event with [params] to all registered JavaScript listeners.
     *
     * Uses [DeviceEventManagerModule.RCTDeviceEventEmitter] — the standard RN
     * mechanism for pushing events from native to JS outside of a direct method call.
     *
     * @param eventName The JS event name (must match the listener registered in JS).
     * @param params    The event payload, or `null` for events with no data.
     */
    private fun sendEvent(eventName: String, params: WritableMap?) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    // -------------------------------------------------------------------------
    // Private — utilities
    // -------------------------------------------------------------------------

    /**
     * Returns the camera executor, re-creating it if it was previously shut down.
     *
     * This guards against the case where [onCatalystInstanceDestroy] shuts down
     * the executor and the module is subsequently reused (e.g. on hot reload),
     * which would otherwise cause a [java.util.concurrent.RejectedExecutionException]
     * when submitting work to the terminated executor.
     */
    private fun requireCameraExecutor(): ExecutorService {
        if (cameraExecutor.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }
        return cameraExecutor
    }

    /**
     * Returns `true` if the CAMERA permission has been granted by the user.
     *
     * @param activity The current [Activity] used to check permission state.
     */
    private fun hasCameraPermission(activity: Activity): Boolean =
        ContextCompat.checkSelfPermission(
            activity,
            android.Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

}