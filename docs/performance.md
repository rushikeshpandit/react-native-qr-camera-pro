# Performance Optimization in `react-native-qr-camera-pro`

`react-native-qr-camera-pro` is engineered for optimal performance, ensuring a smooth user experience even in demanding scanning environments. Several key strategies are employed to achieve this across both iOS and Android platforms.

## General Performance Principles

-   **Native Implementations**: By leveraging Swift for iOS and Kotlin with CameraX for Android, the module utilizes highly optimized platform-specific APIs, avoiding performance bottlenecks often associated with cross-platform abstractions or JavaScript-heavy processing.
-   **New React Native Architecture (TurboModules & Fabric)**:
    -   **TurboModules**: Reduce JavaScript-native bridge overhead for imperative calls and event emission, leading to faster communication.
    -   **Fabric**: Enables efficient rendering of the native camera preview component directly on the native UI thread, ensuring a fluid camera feed without React Native's reconciliation delays.
-   **Zero Unnecessary Dependencies**: Minimizing external libraries reduces the overall footprint, startup time, and potential for performance regressions due to third-party code.

## Key Optimization Strategies

### 1. Throttling Scan Events

-   **Problem**: Continuously sending barcode scan events to the JavaScript bridge can lead to excessive communication, potentially bottlenecking the UI thread and consuming unnecessary resources.
-   **Solution**: A configurable `scanThrottleInterval` (defaulting to 500ms) is implemented on the native side. After a barcode is detected and sent to JavaScript, subsequent detections are ignored for this interval. This ensures that the JavaScript side receives updates at a manageable rate, preventing UI freezes and maintaining responsiveness.

### 2. Duplicate Scan Prevention

-   **Problem**: In scenarios where a user holds a barcode steady in front of the camera, the scanner might rapidly detect and send the same barcode multiple times, leading to redundant processing in the JavaScript application.
-   **Solution**: The native module caches the `lastScannedCode` and ignores any immediate re-detections of the identical barcode. This logic works in conjunction with throttling to provide a cleaner stream of unique scan results.

### 3. Efficient Frame Processing

-   **iOS (AVFoundation)**:
    -   `AVCaptureMetadataOutput` is highly optimized for detecting machine-readable codes directly within the camera feed.
    -   The `setMetadataObjectsDelegate` method specifies a dedicated `DispatchQueue` for processing metadata objects, ensuring that frame analysis happens off the main thread.
-   **Android (CameraX + ML Kit Barcode Scanning)**:
    -   **CameraX `ImageAnalysis`**: Configured with `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` to drop older frames if processing falls behind, ensuring that only the most current frame is analyzed.
    -   **ML Kit Barcode Scanning**: Leverages Google's machine learning capabilities, which are highly optimized for fast and accurate barcode detection.
    -   **Dedicated Executor**: `ImageAnalysis.setAnalyzer` is provided with an `ExecutorService` (`cameraExecutor`) to process image frames on a background thread, preventing UI lag.

### 4. Lifecycle Management

-   **Problem**: Improper management of camera resources can lead to battery drain, crashes, or conflicts with other applications.
-   **Solution**:
    -   **iOS**: The `AVCaptureSession` is started and stopped based on `startScanning()` and `stopScanning()` calls, and can be paused/resumed in response to application foreground/background events (though explicit implementation for app lifecycle is handled via `startScanning`/`stopScanning` for now, it's designed to be lifecycle-aware).
    -   **Android**: `CameraX` is inherently lifecycle-aware. By binding use cases to the `LifecycleOwner` of the current `Activity`, camera resources are automatically managed (initialized when active, released when paused/stopped), minimizing resource consumption.

### 5. Threading Model

-   **iOS**: `AVCaptureSession` delegate calls are configured to occur on a dedicated `DispatchQueue` (e.g., `DispatchQueue.main` for simplicity in the current implementation, but can be easily moved to a background queue for heavy processing) to offload work from the main UI thread.
-   **Android**: All camera frame analysis and ML Kit processing occur on a dedicated `ExecutorService` (`cameraExecutor`), ensuring that computationally intensive tasks do not block the main thread and maintain a fluid UI.

By meticulously implementing these performance considerations, `react-native-qr-camera-pro` aims to deliver a responsive, efficient, and reliable barcode scanning experience for React Native applications.