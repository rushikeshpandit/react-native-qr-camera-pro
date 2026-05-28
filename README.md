# react-native-qr-camera-pro

## Lightweight & High-Performance QR/Barcode Scanner for React Native

`react-native-qr-camera-pro` is a robust, production-grade QR and barcode scanner library meticulously crafted for React Native applications. It leverages native Swift for iOS and Kotlin with CameraX and ML Kit for Android, ensuring optimal performance, zero unnecessary dependencies, and full compatibility with the New React Native Architecture (Fabric and TurboModules).

### Features

-   **Fast** -- Native implementation (Swift & Kotlin) for high performance.
-   **Cross-platform** -- Works seamlessly on both Android and iOS.
-   **New Architecture Ready** -- Supports Fabric & TurboModules for enhanced communication and reduced bridge overhead.
-   **Zero Dependencies** -- Built with minimal external libraries for a lightweight footprint.
-   **Multiple Barcode Formats** -- Scans QR codes, EAN-8, EAN-13, PDF-417, Aztec, Code-128, Code-39, Code-93, Data Matrix, ITF, and UPC-E.
-   **Torch Control** -- Easily toggle the camera's flashlight.
-   **Customizable Overlay UI** -- Provides a native view component that can be styled and integrated into your app's UI.
-   **Lifecycle Aware** -- Handles camera lifecycle, permissions, and threading efficiently.
-   **Strict TypeScript** -- Designed with strict typing for improved developer experience and code maintainability.
-   **MIT Licensed**

## Installation

First, install the library using `yarn`:

```bash
yarn add react-native-qr-camera-pro
```

### iOS Setup

1.  **Install Pods:**
    Navigate to your `ios` directory and install the pods:
    ```bash
    cd ios && pod install && cd ..
    ```
2.  **Add Camera Usage Description:**
    Open `ios/<YourProjectName>/Info.plist` and add the `NSCameraUsageDescription` key:
    ```xml
    <key>NSCameraUsageDescription</key>
    <string>$(PRODUCT_NAME) needs access to your camera to scan QR codes.</string>
    ```

### Android Setup

1.  **Add Camera Permission:**
    Ensure you have the `CAMERA` permission in your `android/app/src/main/AndroidManifest.xml`:
    ```xml
    <uses-permission android:name="android.permission.CAMERA" />
    ```
    (This should already be added by `react-native-qr-camera-pro` if you followed the instructions in this file.)

2.  **Enable AndroidX & Kotlin (if not already):**
    Ensure your Android project is using AndroidX and Kotlin. `react-native-qr-camera-pro` is built with Kotlin. Your `android/build.gradle` should typically have `kotlin-gradle-plugin`.

## Usage

### 1. Basic Component Usage

The primary way to use the scanner is through the `QrCameraProView` component.

```tsx
import React, { useState, useCallback } from 'react';
import { StyleSheet, View, Text, Button, SafeAreaView, Alert } from 'react-native';
import {
  QrCameraProView,
  startScanning,
  stopScanning,
  toggleTorch,
  useBarcodeScanner,
  Barcode, // Type for scanned result
} from 'react-native-qr-camera-pro';

export default function App() {
  const [scannedData, setScannedData] = useState<string | null>(null);
  const [scannedType, setScannedType] = useState<string | null>(null);
  const [isTorchOn, setIsTorchOn] = useState(false);

  const handleBarcodeScanned = useCallback((barcode: Barcode) => {
    console.log('Barcode Scanned:', barcode);
    setScannedData(barcode.data);
    setScannedType(barcode.type);
    // Optionally stop scanning after first successful scan
    // stopScanning();
  }, []);

  // Use the custom hook to listen for barcode scanned events
  useBarcodeScanner(handleBarcodeScanned);

  const handleStartScanning = useCallback(() => {
    startScanning();
    setScannedData(null); // Clear previous scan data
    setScannedType(null);
  }, []);

  const handleStopScanning = useCallback(() => {
    stopScanning();
  }, []);

  const handleToggleTorch = useCallback(() => {
    const newState = !isTorchOn;
    toggleTorch(newState);
    setIsTorchOn(newState);
  }, [isTorchOn]);

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>QR/Barcode Scanner Pro Example</Text>
      <View style={styles.cameraContainer}>
        {/* The camera preview component */}
        <QrCameraProView style={styles.camera} />

        {/* Optional overlay to display scanned results */}
        {scannedData && (
          <View style={styles.overlay}>
            <Text style={styles.overlayText}>Scanned Data: {scannedData}</Text>
            <Text style={styles.overlayText}>Type: {scannedType}</Text>
          </View>
        )}
      </View>
      <View style={styles.buttonContainer}>
        <Button title="Start Scanning" onPress={handleStartScanning} />
        <Button title="Stop Scanning" onPress={handleStopScanning} />
        <Button title={`Torch ${isTorchOn ? 'Off' : 'On'}`} onPress={handleToggleTorch} />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginVertical: 10,
  },
  cameraContainer: {
    flex: 1,
    backgroundColor: 'black',
    marginHorizontal: 20,
    borderRadius: 10,
    overflow: 'hidden',
    position: 'relative',
  },
  camera: {
    flex: 1,
  },
  overlay: {
    position: 'absolute',
    bottom: 20,
    left: 20,
    right: 20,
    backgroundColor: 'rgba(0,0,0,0.7)',
    padding: 10,
    borderRadius: 5,
    alignItems: 'center',
  },
  overlayText: {
    color: 'white',
    fontSize: 16,
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    padding: 20,
  },
});
```

### 2. Imperative API

You can control the scanner imperatively using the exported functions:

-   `startScanning()`: Begins the scanning process.
-   `stopScanning()`: Halts the scanning process.
-   `toggleTorch(enabled: boolean)`: Turns the camera's flashlight on (`true`) or off (`false`).

```tsx
import { startScanning, stopScanning, toggleTorch } from 'react-native-qr-camera-pro';

// To start scanning
startScanning();

// To stop scanning
stopScanning();

// To turn on torch
toggleTorch(true);

// To turn off torch
toggleTorch(false);
```

### 3. Listening for Scan Results (Custom Hook)

The `useBarcodeScanner` hook provides a convenient way to subscribe to barcode scan events:

```tsx
import { useBarcodeScanner, Barcode } from 'react-native-qr-camera-pro';

function MyScannerComponent() {
  const handleScannedCode = (barcode: Barcode) => {
    console.log(`Scanned: ${barcode.data} (Type: ${barcode.type})`);
    // Process the scanned barcode
  };

  useBarcodeScanner(handleScannedCode);

  // ... rest of your component
}
```

## API Reference

### `QrCameraProView` Component

A React Native component that displays the camera feed and acts as the scanning surface.

#### Props

| Prop Name         | Type                              | Description                                                                                             | Default |
| :---------------- | :-------------------------------- | :------------------------------------------------------------------------------------------------------ | :------ |
| `style`           | `ViewStyle`                       | Standard React Native style props for `View`.                                                           |         |
| `onBarcodeScanned`| `(event: { nativeEvent: Barcode }) => void` | Callback function invoked when a barcode is successfully scanned. The `nativeEvent` contains `data` (string) and `type` (string). | `undefined` |
| `(Other props will be added as needed)` | | | |

### Imperative Functions

-   `startScanning(): void`
-   `stopScanning(): void`
-   `toggleTorch(enabled: boolean): void`

### Types

-   `Barcode`: `{ data: string; type: string; }`

## Use Cases

-   **Retail/Inventory Management**: Quickly scan product barcodes for stock checks or sales.
-   **Event Check-in**: Scan QR codes on tickets for seamless entry management.
-   **Logistics/Shipping**: Track packages by scanning shipping labels.
-   **Payment Apps**: Integrate QR code payments.
-   **Authentication**: Scan QR codes for secure login or two-factor authentication.

## Contributing

PRs are welcome!

1.  Fork the repo
2.  Create a feature branch
3.  Commit changes
4.  Open a pull request

## License

MIT © Rushikesh Pandit

Built with ❤️ for the React Native community.

---

Made with [create-react-native-library](https://github.com/callstack/react-native-library)
