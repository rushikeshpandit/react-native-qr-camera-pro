# Performance in `react-native-qr-camera-pro`

This document covers every performance-relevant decision in the library — from frame processing and event throttling through to threading, lifecycle management, and bundle footprint. Architecture decisions (class responsibilities, layer boundaries, flow diagrams) are in [`architecture.md`](./architecture.md).

---

## 1. Native-First Implementation

All camera access, frame analysis, and barcode decoding run entirely in native code:

- **iOS**: `AVFoundation` + `AVCaptureMetadataOutput` — Apple's own machine-readable code detection pipeline, compiled to machine code and running outside the JS runtime entirely.
- **Android**: `CameraX` + `ML Kit Barcode Scanning` — Google's Jetpack camera abstraction backed by `Camera2`, with ML Kit's hardware-accelerated detector.

No JavaScript is involved in frame processing. The JS bridge is touched only once per throttle window (at most every 500 ms) to deliver a scan result.

---

## 2. New Architecture (TurboModules + Fabric)

### TurboModules — imperative calls and event emission

TurboModules replace the asynchronous JSON-serialisation bridge with a synchronous JSI (JavaScript Interface) call. The practical impact for this library:

- `startScanning()`, `stopScanning()`, `toggleTorch()` are direct C++ function calls — no JSON encoding, no async queue hop.
- `onBarcodeScanned` and `onCameraError` events are dispatched through the same JSI path, reducing per-event overhead compared to the old bridge.

### Fabric — native view rendering

The `QrCameraProView` component is rendered by Fabric directly on the native UI thread. The camera preview surface (`AVCaptureVideoPreviewLayer` on iOS, `PreviewView`/`TextureView` on Android) is never routed through React's reconciler — it is a native sublayer/child view that React Native simply sizes and positions. This means the preview frame rate is fully decoupled from the JS render cycle.

---

## 3. Threading Model

Getting threads right is the single most important performance concern for a camera library. Wrong thread usage causes UI jank, ANRs (Android), or main-thread watchdog kills (iOS).

### iOS threading

| Work | Thread | Reason |
|---|---|---|
| `AVCaptureSession` setup and `startRunning()` | `sessionQueue` (background, `.userInitiated`) | Apple's documentation explicitly states `startRunning()` blocks until hardware is ready and must not be called on the main thread |
| `AVCaptureMetadataOutput` delegate callbacks | `DispatchQueue.main` | Metadata object callbacks are lightweight (reading a string value); using main queue avoids an extra context switch for what is already a sub-millisecond operation |
| Torch control (`lockForConfiguration`) | `sessionQueue` | Device configuration lock/unlock is a blocking operation |
| `NotificationCenter` post (session changed) | `DispatchQueue.main` | `QrCameraProView` must touch its layer hierarchy on the main thread (UIKit requirement) |
| `sendEvent` to JS | `DispatchQueue.main` (metadata delegate) | `RCTEventEmitter.sendEvent` is main-thread safe; called from the metadata delegate which already runs on main |

### Android threading

| Work | Thread | Reason |
|---|---|---|
| `ProcessCameraProvider` future listener | Main executor (`ContextCompat.getMainExecutor`) | CameraX requires lifecycle binding on the main thread |
| `bindToLifecycle` / `unbindAll` | UI thread (via `runOnUiThread`) | CameraX lifecycle binding must happen on the main thread |
| `ImageAnalysis.Analyzer.analyze()` | `cameraExecutor` (single-thread background executor) | Frame analysis is CPU/GPU intensive; running it off the main thread prevents UI jank |
| ML Kit `process()` success/failure listeners | `cameraExecutor` (called back on the submitting thread) | Keeps result handling co-located with analysis, avoids extra thread hops |
| `sendEvent` to JS | `cameraExecutor` | `RCTDeviceEventEmitter.emit` is thread-safe; called directly from the ML Kit success listener |
| `setPreviewView` / `bindCameraUseCases` retry | UI thread | CameraX and `view.post` both require the main/UI thread |

### Executor lifecycle (Android)

The `cameraExecutor` is a `Executors.newSingleThreadExecutor()`. It is shut down in `onCatalystInstanceDestroy()`. If the module is recreated after a hot reload (a new `init {}` runs), `requireCameraExecutor()` checks `isShutdown` and creates a fresh executor before submitting any work, preventing `RejectedExecutionException`.

---

## 4. Scan Throttling

### Problem

`AVCaptureMetadataOutput` (iOS) and ML Kit (Android) can fire multiple times per second for a single held barcode. Forwarding every detection to JavaScript would saturate the bridge and cause the JS thread to process far more events than the UI can act on.

### Solution — `BarcodeThrottler`

Both platforms implement an identical `BarcodeThrottler` class that gates emissions with two independent checks:

```
shouldEmit(barcode) → true  when: code is NEW  OR  throttle window has expired
                    → false when: same code AND inside the 500 ms window
```

The checks are evaluated atomically in one call. Crucially, `lastEmitTime` is **always** updated when either condition passes — this was a bug in earlier versions where nesting the dedup check inside the throttle check caused `lastEmitTime` to go stale on repeated same-code detections, making the next scan of any code pass immediately regardless of timing.

The throttler is **reset** (`reset()`) on every `stopScanning()` call so stale state does not carry over into the next session.

### Default interval

500 ms (`scanThrottleInterval`). This strikes a balance between responsiveness (a human deliberate re-scan takes at least 500 ms) and bridge load.

---

## 5. Efficient Frame Processing

### iOS — `AVCaptureMetadataOutput`

- Metadata detection runs inside the `AVFoundation` pipeline, in the same process as the camera capture graph. Frames are never copied to user space for analysis — the kernel hands off a reference to the same buffer.
- Only the metadata object types explicitly listed in `SupportedBarcodeTypes.all` are decoded. Restricting the type list reduces the work the hardware decoder performs per frame.

### Android — CameraX `ImageAnalysis` + ML Kit

- `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`: if `BarcodeAnalyzer.analyze()` is still processing frame N when frame N+1 arrives, frame N+1 replaces the pending frame rather than queuing behind it. This ensures the analyzer always works on the freshest available image rather than building up a backlog.
- ML Kit's barcode scanner uses on-device models optimised for mobile hardware. On devices with a Neural Processing Unit (NPU) or DSP, the ML Kit runtime routes inference through those accelerators automatically.
- `imageProxy.close()` is called in the `addOnCompleteListener` (not `addOnSuccessListener`) so the CameraX buffer is always released — even on analysis failure. Holding the buffer blocks the pipeline and eventually drops the camera feed.
- The `BarcodeScanning` client is created once per `BarcodeAnalyzer` instance and `close()`d in `BarcodeAnalyzer.close()` (called from `releaseCamera()`). Recreating the client on every frame would re-initialise the ML runtime on each analysis call.

---

## 6. Lifecycle Management

### iOS

`AVCaptureSession.startRunning()` / `stopRunning()` are called only in response to explicit `startScanning()` / `stopScanning()` JS calls. The session is torn down completely on `stopScanning()` — `captureSession`, `videoInput`, `metadataOutput`, and `captureDevice` are all set to `nil`. This releases the camera hardware immediately rather than holding it idle, which matters for battery life and multi-app fairness.

### Android

CameraX binds use cases to the `Activity`'s `LifecycleOwner`. When the activity moves to `STOPPED` (e.g. home button, incoming call), CameraX automatically pauses the session. When the activity returns to `STARTED`, CameraX resumes it. This means the library is automatically lifecycle-aware for background/foreground transitions without any additional code.

On explicit `stopScanning()`, `cameraProvider.unbindAll()` is called immediately, releasing the camera hardware and the `BarcodeAnalyzer` ML Kit client.

---

## 7. Bundle Footprint

- **No JavaScript-side image processing** — no `canvas`, no `WebGL`, no frame processing in JS.
- **`camera-video` excluded** from the Android Gradle dependencies — the library uses `Preview` + `ImageAnalysis` only; pulling `camera-video` would add recording infrastructure with no callers.
- **Zero JS-side runtime dependencies** beyond React Native itself — the library adds nothing to the JS bundle other than its own ~3 KB of TypeScript.
- **ML Kit on-device model** is downloaded via Google Play Services on first use (Android), not bundled in the APK, keeping the initial download size unchanged.

---

## 8. React JS-Layer Performance

### Stable `NativeEventEmitter`

The `NativeEventEmitter` instance is created **once at module load time** (outside any component), not inside a `useEffect`. This means:

- No emitter recreation on re-renders.
- No subscription teardown/recreation caused by reference instability.

### Stable-ref hook pattern

`useBarcodeScanner` and `useCameraError` subscribe once on mount and unsubscribe once on unmount regardless of how many times the parent component re-renders or passes a new callback reference. The listener count on the native side therefore stays at 1 for the lifetime of the component — `startObserving` fires exactly once and `stopObserving` fires exactly once.

This prevents the "Sending `onBarcodeScanned` with no listeners registered" warning that occurs when the listener count momentarily drops to zero between a remove and re-add caused by callback reference churn.