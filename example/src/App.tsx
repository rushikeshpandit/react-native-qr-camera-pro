/**
 * @file App.tsx
 *
 * Example application demonstrating the full public API of
 * `react-native-qr-camera-pro`.
 *
 * Demonstrates:
 *  - Camera permission handling on Android (runtime) and iOS (system prompt).
 *  - Rendering the native camera preview with `QrCameraProView`.
 *  - Subscribing to scan results with `useBarcodeScanner`.
 *  - Subscribing to camera errors with `useCameraError`.
 *  - Imperative control via `startScanning`, `stopScanning`, `toggleTorch`.
 */

import { useState, useCallback, useEffect } from 'react';
import {
  StyleSheet,
  View,
  Text,
  Button,
  Alert,
  Platform,
  PermissionsAndroid,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import {
  QrCameraProView,
  startScanning,
  stopScanning,
  toggleTorch,
  useBarcodeScanner,
  useCameraError,
} from 'react-native-qr-camera-pro';
import type { Barcode, CameraErrorEvent } from 'react-native-qr-camera-pro';

// ---------------------------------------------------------------------------
// Permission state
// ---------------------------------------------------------------------------

/**
 * Tri-state permission status:
 *  - `null`  — not yet checked (initial render, before async check completes).
 *  - `true`  — granted.
 *  - `false` — denied or restricted.
 */
type PermissionStatus = boolean | null;

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export default function App() {
  const [scannedData, setScannedData] = useState<string | null>(null);
  const [scannedType, setScannedType] = useState<string | null>(null);
  const [isTorchOn, setIsTorchOn] = useState(false);
  const [hasPermission, setHasPermission] = useState<PermissionStatus>(null);

  // -------------------------------------------------------------------------
  // Permission handling
  // -------------------------------------------------------------------------

  /**
   * Requests the CAMERA permission at runtime (Android only).
   *
   * On Android, permission is a dangerous permission that must be explicitly
   * granted by the user. If granted, scanning starts immediately.
   * If denied, an alert guides the user to the system settings.
   *
   * iOS: this function is not called on iOS — the system permission prompt is
   * triggered automatically by AVFoundation when `startScanning()` is called
   * for the first time, driven by `NSCameraUsageDescription` in `Info.plist`.
   */
  const requestAndroidCameraPermission = useCallback(async () => {
    try {
      const result = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.CAMERA,
        {
          title: 'Camera Permission',
          message: 'This app needs access to your camera to scan QR codes.',
          buttonNeutral: 'Ask Me Later',
          buttonNegative: 'Cancel',
          buttonPositive: 'OK',
        }
      );

      if (result === PermissionsAndroid.RESULTS.GRANTED) {
        setHasPermission(true);
        startScanning();
      } else {
        setHasPermission(false);
        Alert.alert(
          'Permission Required',
          'Camera access is required to scan QR codes. Please enable it in Settings.'
        );
      }
    } catch {
      setHasPermission(false);
      Alert.alert('Permission Error', 'Failed to request camera permission.');
    }
  }, []);

  /**
   * Checks the current CAMERA permission status without prompting the user.
   *
   * Called once on mount. On iOS, the initial state is set to `null` and
   * updated to `true` / `false` based on whether the system has previously
   * recorded a decision; on first launch it is treated as `null` (unknown)
   * and the AVFoundation prompt will appear when `startScanning()` is called.
   *
   * On Android, `PermissionsAndroid.check()` returns the current grant status
   * without showing a dialog.
   */
  const checkInitialPermission = useCallback(async () => {
    if (Platform.OS === 'android') {
      const granted = await PermissionsAndroid.check(
        PermissionsAndroid.PERMISSIONS.CAMERA
      );
      setHasPermission(granted);
    } else {
      // iOS: we don't have a way to check without importing a third-party
      // library. Leave as `null` (unknown) — the system prompt fires
      // automatically on the first `startScanning()` call.
      // Set `true` only after permission is confirmed via app settings flow
      // or if you integrate react-native-permissions.
      setHasPermission(null);
    }
  }, []);

  // Run the permission check once when the component mounts.
  useEffect(() => {
    checkInitialPermission();
  }, [checkInitialPermission]);

  // -------------------------------------------------------------------------
  // Event subscriptions (library hooks — no NativeModules / NativeEventEmitter
  // needed in App code; all bridge plumbing is encapsulated in the library)
  // -------------------------------------------------------------------------

  /**
   * Called by `useBarcodeScanner` each time the native layer emits a new,
   * throttle-gated barcode. Stores the result for display.
   */
  const handleBarcodeScanned = useCallback((barcode: Barcode) => {
    setScannedData(barcode.data);
    setScannedType(barcode.type);
  }, []);

  /**
   * Called by `useCameraError` when the native layer encounters a camera
   * error (e.g. permission denied, device unavailable, use-case binding
   * failure). Shown to the user as an alert.
   */
  const handleCameraError = useCallback((error: CameraErrorEvent) => {
    Alert.alert('Camera Error', error.message);
  }, []);

  // Subscribe for the lifetime of this component.
  useBarcodeScanner(handleBarcodeScanned);
  useCameraError(handleCameraError);

  // -------------------------------------------------------------------------
  // Button handlers
  // -------------------------------------------------------------------------

  /**
   * Handles the "Start Scanning" button.
   *
   * On Android, requests permission first if it has not been granted. On iOS,
   * calls `startScanning()` directly (AVFoundation handles the prompt). Also
   * clears any previously displayed scan result.
   */
  const handleStartScanning = useCallback(() => {
    if (Platform.OS === 'android' && hasPermission !== true) {
      requestAndroidCameraPermission();
    } else {
      startScanning();
      setScannedData(null);
      setScannedType(null);
    }
  }, [hasPermission, requestAndroidCameraPermission]);

  /**
   * Stops the camera session and releases native resources.
   */
  const handleStopScanning = useCallback(() => {
    stopScanning();
  }, []);

  /**
   * Toggles the device torch and keeps the local UI state in sync.
   */
  const handleToggleTorch = useCallback(() => {
    const newState = !isTorchOn;
    toggleTorch(newState);
    setIsTorchOn(newState);
  }, [isTorchOn]);

  // -------------------------------------------------------------------------
  // Render helpers
  // -------------------------------------------------------------------------

  /**
   * Renders the camera preview area.
   *
   * Shows a spinner while permission status is being determined, a prompt when
   * permission is denied, and the live preview once permission is known to be
   * granted (or on iOS where the status is initially unknown).
   */
  const renderCameraArea = () => {
    if (hasPermission === false) {
      return (
        <View style={styles.permissionPrompt}>
          <Text style={styles.permissionText}>
            Camera permission is required to scan barcodes.
          </Text>
          <Button
            title="Grant Permission"
            onPress={requestAndroidCameraPermission}
          />
        </View>
      );
    }

    // `null` on Android means the check is still running — show a spinner.
    // On iOS `null` is the permanent initial state; render the view anyway
    // so the AVFoundation prompt can fire when startScanning() is called.
    if (hasPermission === null && Platform.OS === 'android') {
      return <ActivityIndicator style={styles.spinner} />;
    }

    return <QrCameraProView style={styles.camera} />;
  };

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------

  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.container}>
        <Text style={styles.title}>QR / Barcode Scanner Pro</Text>

        <View style={styles.cameraContainer}>
          {renderCameraArea()}

          {/* Scan result overlay — only visible after a successful scan */}
          {scannedData != null && (
            <View style={styles.overlay}>
              <Text style={styles.overlayText}>Data: {scannedData}</Text>
              <Text style={styles.overlayText}>Type: {scannedType}</Text>
            </View>
          )}
        </View>

        <View style={styles.buttonRow}>
          <Button
            title="Start"
            onPress={handleStartScanning}
            disabled={hasPermission === false}
          />
          <Button
            title="Stop"
            onPress={handleStopScanning}
            disabled={hasPermission === false}
          />
          <Button
            title={`Torch ${isTorchOn ? 'Off' : 'On'}`}
            onPress={handleToggleTorch}
            disabled={hasPermission === false}
          />
        </View>
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

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
  },
  camera: {
    flex: 1,
  },
  permissionPrompt: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
    gap: 12,
  },
  permissionText: {
    color: 'white',
    textAlign: 'center',
    fontSize: 15,
  },
  spinner: {
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
  buttonRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    padding: 20,
  },
});
