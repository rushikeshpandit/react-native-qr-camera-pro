package com.qrcamerapro

import com.facebook.react.bridge.ReactApplicationContext

class QrCameraProModule(reactContext: ReactApplicationContext) :
  NativeQrCameraProSpec(reactContext) {

  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }

  companion object {
    const val NAME = NativeQrCameraProSpec.NAME
  }
}
