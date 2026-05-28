# Architecture of `react-native-qr-camera-pro`

`react-native-qr-camera-pro` is designed for high performance and seamless integration with React Native applications, leveraging the strengths of native platforms and the New React Native Architecture (TurboModules and Fabric).

## High-Level Overview

The module consists of three main layers:

1.  **JavaScript/TypeScript Layer (React Native)**: This layer exposes the public API to React Native developers, including a `<QrCameraProView />` component for displaying the camera feed and imperative functions (`startScanning`, `stopScanning`, `toggleTorch`) for control. It also provides a custom hook (`useBarcodeScanner`) for subscribing to scan events.
2.  **Native Bridge Layer (TurboModules)**: This acts as the communication layer between JavaScript and the platform-specific native code. TurboModules provide faster and type-safe communication compared to the old Bridge.
3.  **Native Platform Layer (iOS Swift, Android Kotlin)**: This is where the core camera and barcode scanning logic resides, implemented using platform-specific APIs for optimal performance. Fabric is utilized for the native UI component, enabling efficient rendering.

```
+--------------------------+     +------------------------+     +-----------------------------+
|    React Native App      |     |  Native Bridge (RN)    |     |     Native Platform         |
|  (JavaScript/TypeScript) |     | (TurboModules & Fabric)|     | (iOS Swift / Android Kotlin)|
+--------------------------+     +------------------------+     +-----------------------------+
|                          |     |                        |     |                             |
|  - <QrCameraProView />   |<--->|  - Native QrCameraPro  |<--->|  - iOS: AVCaptureSession    |
|  - startScanning()       |     |    Module (Generated)  |     |  - Android: CameraX         |
|  - stopScanning()        |     |                        |     |  - iOS: AVFoundation        |
|  - toggleTorch()         |     |  - Native View Manager |     |  - Android: ML Kit Barcode  |
|  - useBarcodeScanner()   |     |    (Fabric Generated)  |     |                             |
|                          |     |                        |     |                             |
+--------------------------+     +------------------------+     +-----------------------------+
```

## Native Layer Details

### iOS (Swift)

-   **Camera Session Management**: `AVCaptureSession` is used for managing the camera input and output. It provides fine-grained control over the camera.
-   **QR/Barcode Detection**: `AVCaptureMetadataOutput` is configured to detect various metadata object types, including QR codes and common barcode formats. The `AVCaptureMetadataOutputObjectsDelegate` protocol is implemented to receive scanned data.
-   **Camera Preview**: The `AVCaptureVideoPreviewLayer` is used to display the real-time camera feed. This layer is integrated into a custom `UIView` (`QrCameraProView`) managed by the Fabric UI component.
-   **Torch Control**: `AVCaptureDevice` properties are used to control the device's torch (flashlight).
-   **Permissions**: `NSCameraUsageDescription` is required in `Info.plist`, and runtime permission requests are handled.

### Android (Kotlin)

-   **Camera Session Management**: `CameraX` is the primary library used for camera integration. It simplifies camera app development, providing a consistent API across Android versions and handling lifecycle management, preview, image analysis, and video capture.
-   **QR/Barcode Detection**: `ML Kit Barcode Scanning` is integrated with `CameraX`'s `ImageAnalysis` use case. `ImageAnalysis.Analyzer` processes camera frames, passing them to ML Kit for barcode detection.
-   **Camera Preview**: `PreviewView` from CameraX is used to display the camera feed. This view is integrated into a custom `FrameLayout` (`QrCameraProView`) managed by the Fabric UI component.
-   **Torch Control**: `CameraControl` within CameraX is used to enable or disable the torch.
-   **Permissions**: `android.permission.CAMERA` is required in `AndroidManifest.xml`, and runtime permission requests are handled by the React Native Activity.

## Bridge Strategy: TurboModules & Fabric

-   **TurboModules**: For imperative calls (e.g., `startScanning`, `stopScanning`, `toggleTorch`) and event emission (e.g., `onBarcodeScanned`), TurboModules are used. They provide a C++ bridge implementation with type-safe communication between JavaScript and native code, resulting in improved performance and maintainability.
-   **Fabric**: For rendering the camera preview, Fabric (the New React Native Renderer) is utilized. A custom native `UIView` (iOS) or `FrameLayout` (Android) is exposed as a React Native component (`QrCameraProView`), allowing for efficient rendering and direct manipulation on the native UI thread.

## Shared Responsibilities & Challenges

-   **Camera Lifecycle**: Both native implementations handle starting and stopping the camera session in response to JavaScript calls and application lifecycle events.
-   **Permission Handling**: Native modules handle requesting and checking camera permissions.
-   **Frame Processing**: Both platforms efficiently process camera frames for barcode detection.
-   **Duplicate Scan Prevention**: Logic is implemented on the native side to prevent rapid, duplicate scans of the same barcode within a short time frame, ensuring a smoother user experience and reducing unnecessary event emissions.
-   **Throttling**: Scanning events are throttled to avoid overwhelming the JavaScript bridge with too many updates, balancing responsiveness with performance.
-   **Threading**: Native camera operations and image analysis are performed on background threads (e.g., `DispatchQueue` on iOS, `ExecutorService` on Android) to keep the main UI thread responsive.
