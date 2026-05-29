# react-native-qr-camera-pro

**Lightweight, high-performance QR and barcode scanner for React Native.**

Built with Swift (iOS) and Kotlin + CameraX + ML Kit (Android). Fully compatible with the New React Native Architecture (TurboModules + Fabric). Zero JavaScript-side processing — all frame analysis runs in native code.

---

## Features

- **Native-first** — Swift on iOS, Kotlin on Android. No JS frame processing.
- **New Architecture ready** — TurboModules (JSI) + Fabric for minimal bridge overhead.
- **Multiple barcode formats** — QR, EAN-8, EAN-13, PDF-417, Aztec, Code-128, Code-39, Code-93, Data Matrix, ITF, ITF-14, UPC-E.
- **Torch control** — toggle the flashlight at any time.
- **Scan throttling** — configurable native-side rate limiting (default 500 ms) prevents bridge saturation.
- **Lifecycle aware** — CameraX binds to the Activity lifecycle on Android; explicit start/stop on iOS.
- **Strict TypeScript** — all public APIs are fully typed.
- **MIT licensed**

---

## Installation

```bash
# yarn
yarn add react-native-qr-camera-pro

# npm
npm install react-native-qr-camera-pro
```

### iOS

```bash
cd ios && pod install && cd ..
```

Add the camera usage description to `ios/<YourProject>/Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>$(PRODUCT_NAME) needs camera access to scan QR codes.</string>
```

### Android

The library's `AndroidManifest.xml` is merged into your app automatically. No manual manifest edits are required for the CAMERA permission — it is declared in the library manifest and merged at build time.

If your `android/build.gradle` does not already have Kotlin support, add:

```groovy
classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21"
```

---

## Quick Start

```tsx
import React, { useState, useCallback } from 'react';
import { StyleSheet, View, Text, Button } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import {
  QrCameraProView,
  startScanning,
  stopScanning,
  toggleTorch,
  useBarcodeScanner,
  useCameraError,
} from 'react-native-qr-camera-pro';
import type { Barcode, CameraErrorEvent } from 'react-native-qr-camera-pro';

export default function ScannerScreen() {
  const [result, setResult] = useState<Barcode | null>(null);
  const [isTorchOn, setIsTorchOn] = useState(false);

  useBarcodeScanner(useCallback((barcode: Barcode) => {
    setResult(barcode);
  }, []));

  useCameraError(useCallback((error: CameraErrorEvent) => {
    console.error('Camera error:', error.message);
  }, []));

  return (
    <SafeAreaView style={styles.container}>
      <QrCameraProView style={styles.camera} />
      {result && (
        <Text>{result.type}: {result.data}</Text>
      )}
      <View style={styles.buttons}>
        <Button title="Start" onPress={startScanning} />
        <Button title="Stop"  onPress={stopScanning} />
        <Button
          title={`Torch ${isTorchOn ? 'Off' : 'On'}`}
          onPress={() => {
            const next = !isTorchOn;
            toggleTorch(next);
            setIsTorchOn(next);
          }}
        />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  camera:    { flex: 1 },
  buttons:   { flexDirection: 'row', justifyContent: 'space-around', padding: 16 },
});
```

---

## API Reference

### Imperative Functions

All three functions are safe to call at any point. Redundant calls (e.g. `startScanning()` when already scanning) are silently ignored by the native layer.

#### `startScanning(): void`

Starts the camera and barcode scanning session.

- **iOS**: AVFoundation triggers the system camera permission prompt on first call if `NSCameraUsageDescription` is set and permission has not yet been granted.
- **Android**: triggers a runtime `CAMERA` permission request if permission has not yet been granted.

#### `stopScanning(): void`

Stops the scanning session and releases all camera hardware resources. The `QrCameraProView` continues rendering (black) but no frames are processed.

#### `toggleTorch(enabled: boolean): void`

Turns the device torch on (`true`) or off (`false`). Silently ignored on devices without a torch (most iPads, some low-end Android devices) or when no camera session is active.

| Parameter | Type | Description |
|---|---|---|
| `enabled` | `boolean` | `true` = torch on, `false` = torch off |

---

### React Hooks

#### `useBarcodeScanner(onScanned: (barcode: Barcode) => void): void`

Subscribes to `onBarcodeScanned` events for the lifetime of the calling component.

- Subscribes once on mount, unsubscribes once on unmount.
- Safe to pass an inline arrow function — the subscription is **not** recreated when the callback reference changes. The latest callback is always called via an internal ref.
- Only fires for events that pass the native throttle gate (500 ms default, same code deduplication).

```tsx
useBarcodeScanner((barcode) => {
  console.log(barcode.data, barcode.type);
});
```

#### `useCameraError(onError: (error: CameraErrorEvent) => void): void`

Subscribes to `onCameraError` events for the lifetime of the calling component.

- Same stable-subscription behaviour as `useBarcodeScanner`.
- Fires when the native layer encounters a camera setup or binding failure.

```tsx
useCameraError(({ message }) => {
  Alert.alert('Camera Error', message);
});
```

---

### `QrCameraProView` Component

Renders the live camera preview. Style it like any other `View`.

```tsx
<QrCameraProView style={{ flex: 1 }} />
```

#### Props

| Prop | Type | Description |
|---|---|---|
| `style` | `ViewStyle` | Standard React Native view styles. |
| `onBarcodeScanned` | `(event: { nativeEvent: Barcode }) => void` | Direct native event callback. Prefer `useBarcodeScanner` for most use cases — this prop is provided for class components or single-subscriber scenarios where the hook pattern is not applicable. |
| `...ViewProps` | — | All standard `View` props are forwarded. |

---

### Types

#### `Barcode`

```ts
type Barcode = {
  data: string;  // The decoded string value of the barcode.
  type: string;  // The format identifier (see table below).
};
```

#### `CameraErrorEvent`

```ts
type CameraErrorEvent = {
  message: string;  // Human-readable description of the native error.
};
```

#### `QrCameraProViewProps`

```ts
interface QrCameraProViewProps extends ViewProps {
  onBarcodeScanned?: (event: { nativeEvent: Barcode }) => void;
}
```

---

### Supported Barcode Formats

| `type` string | Format |
|---|---|
| `QR_CODE` | QR Code |
| `EAN_8` | EAN-8 |
| `EAN_13` | EAN-13 |
| `PDF_417` | PDF-417 |
| `AZTEC` | Aztec |
| `CODE_128` | Code 128 |
| `CODE_39` | Code 39 |
| `CODE_93` | Code 93 |
| `DATA_MATRIX` | Data Matrix |
| `ITF` | Interleaved 2 of 5 |
| `ITF_14` | ITF-14 |
| `UPC_E` | UPC-E |

---

## Permission Handling

### iOS

AVFoundation presents the system permission dialog automatically on the first `startScanning()` call. No JS-side permission code is needed. Ensure `NSCameraUsageDescription` is set in `Info.plist` — without it the app will crash when the camera is accessed.

### Android

`QrCameraProModule.startScanning()` calls `ActivityCompat.requestPermissions` automatically when `CAMERA` permission is not yet granted. The runtime dialog is presented to the user; if granted, scanning begins immediately. If denied, an `onCameraError` event is **not** emitted — your app should handle the `PermissionsAndroid` result in JS and show an appropriate UI.

A typical Android permission flow in your app:

```tsx
import { PermissionsAndroid, Platform } from 'react-native';
import { startScanning } from 'react-native-qr-camera-pro';

async function requestAndStart() {
  if (Platform.OS === 'android') {
    const result = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.CAMERA
    );
    if (result !== PermissionsAndroid.RESULTS.GRANTED) return;
  }
  startScanning();
}
```

---

## Further Reading

- [Architecture](./docs/architecture.md) — layer breakdown, data flow diagrams, class responsibilities.
- [Performance](./docs/performance.md) — threading model, throttling design, frame processing, bundle footprint.

---

## Use Cases

- **Retail / inventory** — scan product barcodes for stock checks.
- **Event check-in** — verify QR-coded tickets at entry.
- **Logistics** — track packages by scanning shipping labels.
- **Payments** — read QR codes for payment flows.
- **Authentication** — QR-based 2FA or login.

---

## Contributing

1. Fork the repository.
2. Create a feature branch (`git checkout -b feat/my-feature`).
3. Commit your changes.
4. Open a pull request.

Please ensure new native code follows the same SRP/SOLID conventions established in the existing iOS and Android source.

---

## License

MIT © Rushikesh Pandit

Built with ❤️ for the React Native community.  
Bootstrapped with [create-react-native-library](https://github.com/callstack/react-native-library).