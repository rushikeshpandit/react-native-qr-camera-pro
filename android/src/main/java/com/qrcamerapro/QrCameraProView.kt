package com.qrcamerapro

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.view.PreviewView

class QrCameraProView(context: Context) : FrameLayout(context) {
    private val TAG = "QrCameraProView"
    val previewView: PreviewView = PreviewView(context)

    // FIX 1: React Native's layout engine calls layout() directly on this root view but
    // doesn't always cascade requestLayout() down the hierarchy. Without this override,
    // the PreviewView child is never remeasured from RN's side and renders at 0x0
    // internally — giving a blank/black preview even though the camera is bound.
    private val measureAndLayout = Runnable {
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        layout(left, top, right, bottom)
    }

    init {
        previewView.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        addView(previewView)
    }

    override fun requestLayout() {
        super.requestLayout()
        // post() so this runs after the current layout pass, not during it
        post(measureAndLayout)
    }

    // FIX 2: Remove the explicit previewView.layout() call.
    // FrameLayout.onLayout (called via super) already lays out all children.
    // The extra call was redundant and could fight with the normal layout pass.
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, "onAttachedToWindow: ${width}x${height}")
        QrCameraProModule.instance?.setPreviewView(previewView)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(TAG, "onDetachedFromWindow")
        QrCameraProModule.instance?.setPreviewView(null)
    }
}