# Architecture of `react-native-qr-camera-pro`

`react-native-qr-camera-pro` is designed for high performance and seamless integration with React Native applications, leveraging the New React Native Architecture (TurboModules and Fabric) alongside platform-native camera and barcode APIs.

---

## High-Level Overview

The module is organised into three distinct layers. Each layer has a single, well-defined responsibility and communicates with adjacent layers through a typed contract.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    JavaScript / TypeScript Layer                        │
│                                                                         │
│  QrCameraProView   startScanning()   stopScanning()   toggleTorch()     │
│  useBarcodeScanner()                 useCameraError()                   │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │  TurboModules (JSI) + Fabric
┌──────────────────────────────▼──────────────────────────────────────────┐
│                      Native Bridge Layer                                │
│                                                                         │
│  NativeQrCameraPro (Codegen spec)   QrCameraProViewManager (Fabric)     │
└──────────┬───────────────────────────────────────┬──────────────────────┘
           │                                       │
┌──────────▼───────────────┐         ┌─────────────▼────────────────────┐
│     iOS  (Swift)         │         │       Android (Kotlin)            │
│                          │         │                                   │
│  QrCameraProSwift        │         │  QrCameraProModule                │
│  BarcodeThrottler        │         │  BarcodeThrottler                 │
│  BarcodeTypeMapper       │         │  BarcodeFormatMapper              │
│  QrCameraProView (UIKit) │         │  BarcodeAnalyzer                  │
│  QrCameraProViewManager  │         │  QrCameraProView (FrameLayout)    │
│                          │         │  QrCameraProViewManager           │
└──────────────────────────┘         └───────────────────────────────────┘
```

---

## Layer 1 — JavaScript / TypeScript

**Location**: `src/index.ts`, `src/NativeQrCameraPro.ts`

This layer is the entire public surface of the library. It exposes:

- A **native view component** (`QrCameraProView`) that renders the live camera feed.
- **Imperative functions** (`startScanning`, `stopScanning`, `toggleTorch`) for session control.
- **React hooks** (`useBarcodeScanner`, `useCameraError`) for subscribing to native events.
- **Shared types** (`Barcode`, `CameraErrorEvent`) defined once in `NativeQrCameraPro.ts` and re-exported from `index.ts`.

### Module resolution

```ts
// Prefers the JSI TurboModule path; falls back to the legacy bridge.
const nativeModule =
  TurboModuleRegistry.get('QrCameraPro') ?? NativeModules.QrCameraPro ?? null;
```

### NativeEventEmitter construction

`NativeEventEmitter` is always constructed from `NativeModules.QrCameraPro` (the legacy-bridge reference), **not** from the TurboModule proxy. On the New Architecture, the TurboModule proxy does not implement the `addListener` / `removeListeners` accounting that `NativeEventEmitter` requires, which would suppress the native `startObserving` / `stopObserving` hooks and produce "no listeners registered" warnings.

### Stable-ref hook pattern

Both `useBarcodeScanner` and `useCameraError` use a `useRef` to hold the latest callback and an empty-dependency `useEffect` for the subscription. This ensures:

- The `NativeEventEmitter` subscription is created **once** on mount and torn down **once** on unmount.
- The latest callback is always invoked without requiring the subscription to be recreated when the consumer passes a new function reference (e.g. an inline arrow function on every render).

---

## Layer 2 — Native Bridge (TurboModules & Fabric)

### TurboModules

`NativeQrCameraPro.ts` is the Codegen spec. Codegen reads this file and generates:

- A C++ JSI adapter (`NativeQrCameraProSpecJSI`) that the Obj-C++ bridge (`QrCameraPro.mm`) conforms to on iOS.
- A Kotlin abstract class (`NativeQrCameraProSpec`) that `QrCameraProModule` extends on Android.

The result is type-safe, zero-serialisation communication between JavaScript and native for all imperative calls and event emissions.

### Fabric (native view)

`QrCameraProViewManager` (both platforms) extends the appropriate Fabric view manager base class and returns a `QrCameraProView` instance. The JS `requireNativeComponent('QrCameraProView')` call resolves to this manager, enabling the Fabric renderer to mount and size the native view directly on the UI thread.

---

## Layer 3 — Native Platform

### iOS (Swift)

| File | Responsibility |
|---|---|
| `QrCameraPro.h` / `QrCameraPro.mm` | Obj-C++ TurboModule bridge; forwards JS calls to `QrCameraProSwift`; owns `RCTEventEmitter` listener lifecycle (`startObserving` / `stopObserving`) |
| `QrCameraProSwift.swift` | Owns the `AVCaptureSession` lifecycle; dispatches all session operations on `sessionQueue`; emits events via the `RCTEventEmitter` reference |
| `BarcodeThrottler.swift` | Stateful throttle + dedup logic; isolated from session code (SRP) |
| `BarcodeTypeMapper` (enum in `QrCameraProSwift.swift`) | Maps `AVMetadataObject.ObjectType` → JS string constant |
| `SupportedBarcodeTypes` (enum in `QrCameraProSwift.swift`) | Enumerates the `metadataObjectTypes` list |
| `QrCameraProView.swift` | `UIView` subclass; observes `Notification.Name.qrCameraProSessionChanged`; attaches / removes `AVCaptureVideoPreviewLayer` |
| `QrCameraProViewManager.swift` | `RCTViewManager` subclass; vends `QrCameraProView` instances to Fabric |
| `QrCameraProViewManager.m` | Obj-C shim; registers the Swift view manager and exports the `onBarcodeScanned` direct-event prop |

#### iOS camera session flow

```
startScanning() [JS]
  → QrCameraPro.mm startScanning
    → QrCameraProSwift.shared.startScanning()
      → sessionQueue.async { setupAndStartSession() }
        → buildSessionGraph()          // AVCaptureSession + input + output
        → captureSession.startRunning()
        → postSessionChangedNotification()  // main thread → QrCameraProView
          → QrCameraProView.updatePreviewLayer(for: session)
```

#### iOS event emission flow

```
AVCaptureMetadataOutputObjectsDelegate.metadataOutput(_:didOutput:from:)  [main queue]
  → BarcodeThrottler.shouldEmit(barcode:)
  → BarcodeTypeMapper.string(for: type)
  → RCTEventEmitter.sendEvent(withName: "onBarcodeScanned", body: [...])
    → JS NativeEventEmitter → useBarcodeScanner callback
```

#### iOS listener lifecycle

`QrCameraPro.mm` overrides `addListener` / `removeListeners` (calling `super`) so `RCTEventEmitter` correctly increments/decrements its internal listener count and calls `startObserving` (count 0→1) and `stopObserving` (count →0). `QrCameraProSwift.hasListeners` is set accordingly, gating all `sendEvent` calls to prevent "no listeners" warnings.

---

### Android (Kotlin)

| File | Responsibility |
|---|---|
| `QrCameraProModule.kt` | TurboModule implementation; owns the CameraX session lifecycle; emits events via `DeviceEventManagerModule.RCTDeviceEventEmitter` |
| `BarcodeThrottler.kt` | Stateful throttle + dedup logic; identical contract to the iOS equivalent (SRP) |
| `BarcodeFormatMapper.kt` | Maps ML Kit `Barcode.FORMAT_*` int → JS string constant (SRP / OCP) |
| `BarcodeAnalyzer.kt` | `ImageAnalysis.Analyzer` + `Closeable`; feeds CameraX frames to ML Kit; closes the scanner client on release |
| `QrCameraProView.kt` | `FrameLayout` subclass; hosts `PreviewView`; notifies the module via `QrCameraProModule.instance` on attach/detach |
| `QrCameraProViewManager.kt` | `SimpleViewManager` subclass; vends `QrCameraProView` instances to Fabric |
| `QrCameraProPackage.kt` | `BaseReactPackage`; registers `QrCameraProModule` and `QrCameraProViewManager` with the RN bridge |

#### Android camera session flow

```
startScanning() [JS]
  → QrCameraProModule.startScanning()   [UI thread]
    → hasCameraPermission() → true
      → pendingBind = true
      → initializeCamera()
        → ProcessCameraProvider.getInstance() future resolves  [main executor]
          → bindCameraUseCases()
            → pre-conditions: LifecycleOwner ✓, provider ✓, camera null ✓, view sized ✓
            → cameraProvider.bindToLifecycle(Preview + ImageAnalysis)
              → BarcodeAnalyzer.analyze(imageProxy)  [cameraExecutor]
                → ML Kit process(inputImage)
                  → BarcodeThrottler.shouldEmit()
                  → sendEvent("onBarcodeScanned", ...)
                    → JS NativeEventEmitter → useBarcodeScanner callback
```

#### Android deferred-bind race condition

`startScanning()` can be called before `QrCameraProView` attaches to the window. The `pendingBind` flag handles this: when `setPreviewView()` is later called with a non-null view and `pendingBind` is still true, it triggers `bindCameraUseCases()` on the UI thread, completing the deferred bind.

#### Android view-not-yet-laid-out retry

`bindCameraUseCases()` checks `view.width > 0 && view.height > 0` before binding. If the view is attached but not yet laid out (size 0×0), it posts a single retry via `view.post { bindCameraUseCases() }`, which runs after the next layout pass.

#### Android module singleton

`QrCameraProModule.instance` is a `@Volatile companion object var` set in `init {}` and cleared in `onCatalystInstanceDestroy()`. It is used only by `QrCameraProView` to push the `PreviewView` reference into the module. `@Volatile` ensures cross-thread visibility between the UI thread (view attachment) and the thread on which the module was created.

---

## Cross-Platform Design Decisions

### Single `BarcodeThrottler` contract

Both platforms implement a `BarcodeThrottler` with identical semantics:

- A barcode is suppressed when it matches the last emitted code **and** the throttle window (500 ms default) has not expired.
- Once the window expires, the same code is allowed through again — enabling intentional re-scans.
- `reset()` is called on `stopScanning()` / `tearDownSession()` so stale state does not carry into the next session.

### Permission model

| Platform | Who requests | When |
|---|---|---|
| iOS | AVFoundation (system) | On the first `startScanning()` call that accesses the camera hardware |
| Android | `QrCameraProModule` via `ActivityCompat.requestPermissions` | Inside `startScanning()` when `PERMISSION_GRANTED` check fails |

The JS layer (`index.ts`) does not handle permissions — it delegates entirely to the native layer, keeping the JS API surface minimal.

### Event names

Both platforms emit the same two event names, defined as constants in both the JS layer (`index.ts`) and the native modules:

| Event | Payload | Trigger |
|---|---|---|
| `onBarcodeScanned` | `{ data: string, type: string }` | Successful throttle-gated barcode detection |
| `onCameraError` | `{ message: string }` | Any native camera or binding error |