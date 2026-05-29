package com.qrcamerapro

import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext

/**
 * React Native view manager that creates and owns [QrCameraProView] instances.
 *
 * Registered with the RN bridge via [QrCameraProPackage.createViewManagers].
 * The name returned by [getName] must match the string used on the JS side when
 * referencing the native view component (e.g. `requireNativeComponent('QrCameraProView')`).
 *
 * This class intentionally has no constructor parameters — the [ReactApplicationContext]
 * is not needed here because all camera interaction is handled by [QrCameraProModule].
 */
internal class QrCameraProViewManager : SimpleViewManager<QrCameraProView>() {

    /**
     * Returns the name under which this view manager is registered with React Native.
     * Must be stable across releases — changing it is a breaking JS API change.
     */
    override fun getName(): String = VIEW_NAME

    /**
     * Creates a new [QrCameraProView] instance for React Native to mount into the
     * view hierarchy. Called by the RN UI manager when the JS component is rendered.
     *
     * @param reactContext The themed context scoped to the current React surface.
     * @return A freshly instantiated [QrCameraProView].
     */
    override fun createViewInstance(reactContext: ThemedReactContext): QrCameraProView {
        return QrCameraProView(reactContext)
    }

    companion object {
        /** Stable identifier used to look up this manager in the React Native registry. */
        const val VIEW_NAME = "QrCameraProView"
    }
}