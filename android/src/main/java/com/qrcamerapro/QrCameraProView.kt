package com.qrcamerapro

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.view.PreviewView

/**
 * Root native view for the QR/Barcode scanner component.
 *
 * Responsibilities (SRP):
 *  - Host and size the [PreviewView] that renders the camera feed.
 *  - Notify [QrCameraProModule] when it attaches to / detaches from the window
 *    so the module can bind or unbind the camera preview surface.
 *
 * What this view does NOT do:
 *  - It has no knowledge of barcode scanning, event emission, or camera lifecycle.
 *  - It never starts or stops the camera session itself.
 *
 * @param context Android [Context] provided by React Native's view manager.
 */
internal class QrCameraProView(context: Context) : FrameLayout(context) {

    companion object {
        private const val TAG = "QrCameraProView"
    }

    /**
     * The CameraX surface that renders the live camera preview.
     *
     * Exposed as `internal` so [QrCameraProModule] can attach a [Preview]
     * surface provider to it via [PreviewView.getSurfaceProvider].
     */
    internal val previewView: PreviewView = PreviewView(context).apply {
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        // COMPATIBLE mode uses a TextureView internally, which is more reliable
        // across a wider range of devices than SURFACE_VIEW.
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    /**
     * Runnable that re-measures and re-lays-out this view and all its children.
     *
     * React Native's layout engine calls [layout] directly on this root view but
     * does not always propagate [requestLayout] down the hierarchy. Without the
     * explicit measure/layout pass, [PreviewView] is never remeasured from RN's
     * side and renders at 0×0 internally — producing a black preview even though
     * the camera session is running.
     *
     * The runnable is posted (not run inline) so it executes **after** the current
     * layout pass completes, avoiding re-entrant layout calls.
     */
    private val forceLayoutRunnable = Runnable {
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        layout(left, top, right, bottom)
    }

    init {
        addView(previewView)
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------

    /**
     * Intercepts layout requests from React Native and schedules a full
     * measure/layout pass via [forceLayoutRunnable].
     *
     * Calling `post` here ensures the runnable runs after the current layout
     * pass rather than during it, which would cause re-entrant layout issues.
     */
    override fun requestLayout() {
        super.requestLayout()
        post(forceLayoutRunnable)
    }

    // -------------------------------------------------------------------------
    // Window attachment lifecycle
    // -------------------------------------------------------------------------

    /**
     * Called by Android when this view is added to a window (i.e. when React
     * Native mounts the component into the view hierarchy).
     *
     * Registers [previewView] with the active [QrCameraProModule] instance so
     * the module can attach the camera preview surface. If [startScanning] was
     * called before this view attached, the module will detect the pending bind
     * flag and trigger camera binding now.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, "onAttachedToWindow: ${width}x${height}")
        QrCameraProModule.instance?.setPreviewView(previewView)
    }

    /**
     * Called by Android when this view is removed from its window (i.e. when
     * React Native unmounts the component).
     *
     * Clears the [previewView] reference from the module so the module does not
     * hold a reference to a detached view, which would prevent garbage collection.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(TAG, "onDetachedFromWindow")
        QrCameraProModule.instance?.setPreviewView(null)
    }
}