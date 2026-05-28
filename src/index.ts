import {
  requireNativeComponent,
  NativeEventEmitter,
  NativeModules,
  TurboModuleRegistry,
} from 'react-native';
import type { ViewProps } from 'react-native';
import { useEffect, useRef } from 'react';

// TurboModule/NativeModule lookup
const QrCameraProModule =
  TurboModuleRegistry.get('QrCameraPro') || NativeModules.QrCameraPro;

export const startScanning = () => {
  if (QrCameraProModule) {
    QrCameraProModule.startScanning();
  } else {
    console.warn(
      'QrCameraPro: startScanning called but native module is not available.'
    );
  }
};

export const stopScanning = () => {
  if (QrCameraProModule) {
    QrCameraProModule.stopScanning();
  }
};

export const toggleTorch = (enabled: boolean) => {
  if (QrCameraProModule) {
    QrCameraProModule.toggleTorch(enabled);
  }
};

export interface Barcode {
  data: string;
  type: string;
}

export interface QrCameraProViewProps extends ViewProps {
  onBarcodeScanned?: (event: { nativeEvent: Barcode }) => void;
}

// Singleton emitter — create once, not inside useEffect, so the subscription
// object is stable across re-renders.
const emitter = QrCameraProModule
  ? new NativeEventEmitter(QrCameraProModule)
  : null;

/**
 * Subscribe to barcode scan events.
 *
 * FIX 4: The previous implementation listed `onScanned` as a useEffect
 * dependency. If the consumer passes an inline arrow function (the common
 * case), React creates a new function reference on every render, causing the
 * effect to tear down and re-create the NativeEventEmitter subscription on
 * every render. This momentarily drops the listener count to 0 mid-scan and
 * re-triggers the "no listeners registered" warning.
 *
 * Fix: hold the latest callback in a ref so the subscription itself is stable
 * (empty dependency array = subscribe once, unsubscribe on unmount) while
 * still always calling the most recent version of the callback.
 */
export function useBarcodeScanner(onScanned: (barcode: Barcode) => void) {
  // Always keep a reference to the latest callback without making it a
  // subscription dependency.
  const onScannedRef = useRef(onScanned);
  useEffect(() => {
    onScannedRef.current = onScanned;
  });

  useEffect(() => {
    if (!emitter) {
      console.warn(
        'QrCameraPro: useBarcodeScanner used but native module is not available.'
      );
      return;
    }

    const subscription = emitter.addListener(
      'onBarcodeScanned',
      (event: any) => {
        // Always delegates to the latest callback via ref — no stale closures.
        onScannedRef.current(event as Barcode);
      }
    );

    // Unsubscribe exactly once when the component unmounts.
    return () => subscription.remove();
  }, []); // stable — never re-subscribes due to callback churn
}

export const QrCameraProView =
  requireNativeComponent<QrCameraProViewProps>('QrCameraProView');
