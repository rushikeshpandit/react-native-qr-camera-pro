import { useState, useCallback, useEffect } from 'react';
import {
  StyleSheet,
  View,
  Text,
  Button,
  Alert,
  NativeEventEmitter,
  NativeModules,
  Platform,
  PermissionsAndroid,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import {
  QrCameraProView,
  startScanning,
  stopScanning,
  toggleTorch,
  useBarcodeScanner,
} from 'react-native-qr-camera-pro';
import type { Barcode } from 'react-native-qr-camera-pro';

export default function App() {
  const [scannedData, setScannedData] = useState<string | null>(null);
  const [scannedType, setScannedType] = useState<string | null>(null);
  const [isTorchOn, setIsTorchOn] = useState(false);
  const [hasPermission, setHasPermission] = useState<boolean | null>(null);

  const requestCameraPermission = useCallback(async () => {
    if (Platform.OS === 'android') {
      try {
        const granted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.CAMERA,
          {
            title: 'Camera Permission',
            message: 'This app needs access to your camera to scan QR codes.',
            buttonNeutral: 'Ask Me Later',
            buttonNegative: 'Cancel',
            buttonPositive: 'OK',
          }
        );

        if (granted === PermissionsAndroid.RESULTS.GRANTED) {
          console.log('Camera permission granted');
          setHasPermission(true);
          startScanning();
        } else {
          console.log('Camera permission denied');
          setHasPermission(false);
          Alert.alert(
            'Permission Denied',
            'Camera access is required to scan QR codes.'
          );
        }
      } catch (err) {
        console.warn(err);
        setHasPermission(false);
        Alert.alert('Permission Error', 'Failed to request camera permission.');
      }
    } else if (Platform.OS === 'ios') {
      setHasPermission(true);
      startScanning();
    }
  }, []);

  const handleBarcodeScanned = useCallback((barcode: Barcode) => {
    console.log('Barcode Scanned:', barcode);
    setScannedData(barcode.data);
    setScannedType(barcode.type);
  }, []);

  useBarcodeScanner(handleBarcodeScanned);

  const handleStartScanning = useCallback(() => {
    if (hasPermission === null || hasPermission === false) {
      requestCameraPermission();
    } else {
      startScanning();
      setScannedData(null);
      setScannedType(null);
    }
  }, [hasPermission, requestCameraPermission]);

  const handleStopScanning = useCallback(() => {
    stopScanning();
  }, []);

  const handleToggleTorch = useCallback(() => {
    const newState = !isTorchOn;
    toggleTorch(newState);
    setIsTorchOn(newState);
  }, [isTorchOn]);

  useEffect(() => {
    const QrCameraProNativeModule = NativeModules.QrCameraPro;
    if (!QrCameraProNativeModule) {
      console.error('QrCameraPro native module not found.');
      return;
    }

    const eventEmitter = new NativeEventEmitter(QrCameraProNativeModule);

    const errorSubscription = eventEmitter.addListener(
      'onCameraError',
      (event) => {
        const { message } = event as { message: string };
        console.error('Camera Error:', message);
        Alert.alert('Camera Error', message);
      }
    );

    const checkInitialPermission = async () => {
      if (Platform.OS === 'android') {
        const status = await PermissionsAndroid.check(
          PermissionsAndroid.PERMISSIONS.CAMERA
        );
        setHasPermission(status);
      } else if (Platform.OS === 'ios') {
        setHasPermission(true);
      }
    };
    checkInitialPermission();

    return () => {
      errorSubscription.remove();
    };
  }, []);

  return (
    <SafeAreaProvider>
      <SafeAreaView style={styles.container}>
        <Text style={styles.title}>QR/Barcode Scanner Pro</Text>
        <View style={styles.cameraContainer}>
          {hasPermission !== false && <QrCameraProView style={styles.camera} />}
          {scannedData && (
            <View style={styles.overlay}>
              <Text style={styles.overlayText}>
                Scanned Data: {scannedData}
              </Text>
              <Text style={styles.overlayText}>Type: {scannedType}</Text>
            </View>
          )}
        </View>
        <View style={styles.buttonContainer}>
          {hasPermission === false && (
            <Button
              title="Grant Permission"
              onPress={requestCameraPermission}
            />
          )}
          {(hasPermission === null || hasPermission === true) && (
            <Button title="Start Scanning" onPress={handleStartScanning} />
          )}
          <Button
            title="Stop Scanning"
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
