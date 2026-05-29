/**
 * @file index.ts
 *
 * Public entry point for `react-native-qr-camera-pro`.
 *
 * Exports:
 *  - Imperative API: `startScanning`, `stopScanning`, `toggleTorch`
 *  - React hooks:    `useBarcodeScanner`, `useCameraError`
 *  - Native view:    `QrCameraProView`
 *  - Types:          `Barcode`, `CameraErrorEvent`, `QrCameraProViewProps`
 */

import {
  NativeEventEmitter,
  NativeModules,
  TurboModuleRegistry,
  requireNativeComponent,
  type HostComponent,
} from 'react-native';
import type { ViewProps } from 'react-native';
import { useEffect, useRef } from 'react';

// Re-export shared types from the spec so consumers import from one place.
export type { Barcode, CameraErrorEvent } from './NativeQrCameraPro';
import type { Barcode, CameraErrorEvent } from './NativeQrCameraPro';

// ---------------------------------------------------------------------------
// Module resolution
// ---------------------------------------------------------------------------

/**
 * Resolves the native QrCameraPro module, preferring the TurboModule path
 * (New Architecture) and falling back to the legacy bridge (Old Architecture).
 *
 * Centralised here (DRY) so every exported function and the emitter all use
 * the same resolution result rather than each doing their own lookup.
 *
 * Returns `null` when running in an environment where neither path is
 * available (e.g. unit tests without a native runtime).
 */
function resolveNativeModule() {
  // TurboModuleRegistry.get() returns the JSI-backed TurboModule when the New
  // Architecture is enabled; null otherwise. We use `get` (not `getEnforcing`)
  // here so we can fall back gracefully to the legacy bridge.
  return (
    (TurboModuleRegistry.get(
      'QrCameraPro'
    ) as typeof NativeModules.QrCameraPro) ??
    NativeModules.QrCameraPro ??
    null
  );
}

/** Cached module reference — resolved once at import time. */
const nativeModule = resolveNativeModule();

// ---------------------------------------------------------------------------
// NativeEventEmitter
//
// IMPORTANT: NativeEventEmitter must be constructed from the legacy-bridge
// module (`NativeModules.QrCameraPro`), NOT from the TurboModule proxy.
// On the New Architecture the TurboModule proxy does not implement the
// `addListener` / `removeListeners` accounting that NativeEventEmitter
// expects, which would suppress the `startObserving` / `stopObserving` hooks
// on the native side and produce "no listeners registered" warnings.
//
// We therefore always use `NativeModules.QrCameraPro` for the emitter,
// regardless of which path was used to resolve `nativeModule` above.
// ---------------------------------------------------------------------------

/**
 * Singleton emitter backed by the legacy-bridge module reference.
 *
 * Created once at module load time so that subscription objects are stable
 * across component re-renders (KISS — no emitter recreation in hooks).
 *
 * `null` when the native module is unavailable (e.g. in test environments).
 */
const emitter: NativeEventEmitter | null = NativeModules.QrCameraPro
  ? new NativeEventEmitter(NativeModules.QrCameraPro)
  : null;

// ---------------------------------------------------------------------------
// Event name constants  (DRY — single source of truth, mirrors native side)
// ---------------------------------------------------------------------------

const EVENT_BARCODE_SCANNED = 'onBarcodeScanned' as const;
const EVENT_CAMERA_ERROR = 'onCameraError' as const;

// ---------------------------------------------------------------------------
// Imperative API
// ---------------------------------------------------------------------------

/**
 * Starts the barcode scanning session.
 *
 * On Android this also triggers a CAMERA runtime permission request when
 * permission has not yet been granted. On iOS the system permission prompt
 * is driven by `NSCameraUsageDescription` in `Info.plist`.
 *
 * Safe to call when a session is already running — the native layer ignores
 * redundant calls.
 */
export function startScanning(): void {
  if (!nativeModule) {
    console.warn(
      '[QrCameraPro] startScanning() called but the native module is not available.'
    );
    return;
  }
  nativeModule.startScanning();
}

/**
 * Stops the scanning session and releases all camera resources.
 *
 * Safe to call even when scanning is not currently active.
 */
export function stopScanning(): void {
  if (!nativeModule) {
    console.warn(
      '[QrCameraPro] stopScanning() called but the native module is not available.'
    );
    return;
  }
  nativeModule.stopScanning();
}

/**
 * Turns the device torch (flashlight) on or off.
 *
 * Silently ignored by the native layer when no camera is bound or the device
 * has no torch hardware (e.g. most iPads, some low-end Android devices).
 *
 * @param enabled `true` to turn the torch on; `false` to turn it off.
 */
export function toggleTorch(enabled: boolean): void {
  if (!nativeModule) {
    console.warn(
      '[QrCameraPro] toggleTorch() called but the native module is not available.'
    );
    return;
  }
  nativeModule.toggleTorch(enabled);
}

// ---------------------------------------------------------------------------
// Hooks
// ---------------------------------------------------------------------------

/**
 * Subscribes to barcode scan events for the lifetime of the calling component.
 *
 * The subscription is created once on mount and torn down on unmount — it does
 * **not** re-subscribe when the callback reference changes (which would happen
 * on every render with an inline arrow function). The latest callback is always
 * called via a ref, preventing stale-closure bugs without churning subscriptions.
 *
 * @example
 * ```tsx
 * useBarcodeScanner((barcode) => {
 *   console.log(barcode.data, barcode.type);
 * });
 * ```
 *
 * @param onScanned Callback invoked with each unique / throttle-gated barcode.
 */
export function useBarcodeScanner(onScanned: (barcode: Barcode) => void): void {
  // Hold the latest callback in a ref so the NativeEventEmitter subscription
  // (which has an empty dep array and is created only once) always delegates
  // to the most recently rendered version of the callback. Without the ref,
  // an inline arrow function prop would cause the effect to tear down and
  // re-create the subscription on every render, momentarily dropping the
  // listener count to zero and retriggering the native `startObserving` hook.
  const callbackRef = useRef(onScanned);

  // Intentionally no dependency array — this effect runs synchronously after
  // every render to keep the ref in sync BEFORE the subscription effect fires.
  useEffect(() => {
    callbackRef.current = onScanned;
  });

  useEffect(() => {
    if (!emitter) {
      console.warn(
        '[QrCameraPro] useBarcodeScanner: native module is not available.'
      );
      return;
    }

    const subscription = emitter.addListener(
      EVENT_BARCODE_SCANNED,
      (event: unknown) => {
        // Delegate through ref — always calls the latest callback, never stale.
        callbackRef.current(event as Barcode);
      }
    );

    // Clean up exactly once when the component unmounts.
    return () => subscription.remove();
  }, []); // stable — empty deps intentional, see callbackRef pattern above
}

/**
 * Subscribes to camera error events for the lifetime of the calling component.
 *
 * Uses the same stable-ref pattern as `useBarcodeScanner` to avoid subscription
 * churn when an inline callback is passed.
 *
 * @example
 * ```tsx
 * useCameraError(({ message }) => {
 *   Alert.alert('Camera Error', message);
 * });
 * ```
 *
 * @param onError Callback invoked whenever the native layer emits `onCameraError`.
 */
export function useCameraError(
  onError: (error: CameraErrorEvent) => void
): void {
  // Same stable-ref pattern as useBarcodeScanner — see comment there.
  const callbackRef = useRef(onError);

  useEffect(() => {
    callbackRef.current = onError;
  });

  useEffect(() => {
    if (!emitter) {
      console.warn(
        '[QrCameraPro] useCameraError: native module is not available.'
      );
      return;
    }

    const subscription = emitter.addListener(
      EVENT_CAMERA_ERROR,
      (event: unknown) => {
        callbackRef.current(event as CameraErrorEvent);
      }
    );

    return () => subscription.remove();
  }, []); // stable — empty deps intentional, see callbackRef pattern above
}

// ---------------------------------------------------------------------------
// Native view component
// ---------------------------------------------------------------------------

/**
 * Props accepted by the `QrCameraProView` native component.
 */
export interface QrCameraProViewProps extends ViewProps {
  /**
   * Called when a barcode is detected via the direct native event prop path.
   *
   * Prefer `useBarcodeScanner()` for most use cases — this prop exists for
   * scenarios where the hook pattern is not applicable (e.g. class components,
   * or when a single view instance is the sole subscriber).
   *
   * The `nativeEvent` field contains `data` (string) and `type` (string).
   */
  onBarcodeScanned?: (event: { nativeEvent: Barcode }) => void;
}

/**
 * Native camera preview component.
 *
 * Renders the live camera feed using `AVCaptureVideoPreviewLayer` on iOS and
 * `PreviewView` (CameraX) on Android. Style it like any other `View`.
 *
 * @example
 * ```tsx
 * <QrCameraProView style={{ flex: 1 }} />
 * ```
 */
export const QrCameraProView: HostComponent<QrCameraProViewProps> =
  requireNativeComponent<QrCameraProViewProps>('QrCameraProView');
