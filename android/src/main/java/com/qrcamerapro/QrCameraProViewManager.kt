package com.qrcamerapro

import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.bridge.ReactApplicationContext

class QrCameraProViewManager(
  private val reactContext: ReactApplicationContext
) : SimpleViewManager<QrCameraProView>() {
    override fun getName() = "QrCameraProView"

    override fun createViewInstance(reactContext: ThemedReactContext): QrCameraProView {
        return QrCameraProView(reactContext)
    }
}
