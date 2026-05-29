package com.qrcamerapro

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager
import java.util.Collections

/**
 * React Native package that registers the QR/Barcode scanner's native module
 * and view manager with the RN bridge.
 *
 * Add an instance of this class to the list returned by
 * `ReactNativeHost.getPackages()` in your application.
 *
 * Registered components:
 *  - [QrCameraProModule] — TurboModule for startScanning / stopScanning / toggleTorch.
 *  - [QrCameraProViewManager] — Native view manager for the live camera preview.
 */
class QrCameraProPackage : BaseReactPackage() {

    /**
     * Returns the [NativeModule] for the given [name], or `null` if this package
     * does not own a module with that name.
     *
     * Called by the RN bridge when a JS import triggers module initialisation.
     *
     * @param name         The module name requested by the bridge.
     * @param reactContext The application-scoped React context.
     * @return A new [QrCameraProModule] instance, or `null`.
     */
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
        return if (name == QrCameraProModule.NAME) QrCameraProModule(reactContext) else null
    }

    /**
     * Provides metadata about each [NativeModule] in this package.
     *
     * The bridge uses this to decide whether to eagerly initialise the module and
     * whether it is a TurboModule. Returning accurate metadata avoids unnecessary
     * eager loading.
     *
     * @return A [ReactModuleInfoProvider] whose map contains an entry for [QrCameraProModule].
     */
    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider = ReactModuleInfoProvider {
        mapOf(
            QrCameraProModule.NAME to ReactModuleInfo(
                /* name                  */ QrCameraProModule.NAME,
                /* className             */ QrCameraProModule.NAME,
                /* canOverrideExistingModule */ false,
                /* needsEagerInit        */ false,
                /* hasConstants          */ false, // Module exposes no JS constants.
                /* isCxxModule          */ false,
                /* isTurboModule        */ true,
            ),
        )
    }

    /**
     * Creates the list of [ViewManager]s provided by this package.
     *
     * The [QrCameraProViewManager] is registered here so that React Native can
     * instantiate [QrCameraProView] when the JS component is rendered.
     *
     * @param reactContext The application-scoped React context.
     * @return A singleton list containing [QrCameraProViewManager].
     */
    override fun createViewManagers(
        reactContext: ReactApplicationContext,
    ): List<ViewManager<*, *>> = Collections.singletonList(QrCameraProViewManager())
}