/**
 * @file NativeQrCameraPro.ts
 *
 * React Native Codegen spec for the QrCameraPro TurboModule.
 *
 * This file has two jobs:
 *  1. Define the **TypeScript types** shared across the library
 *     (`Barcode`, `CameraErrorEvent`) — single source of truth, re-exported
 *     from `index.ts` so consumers never import directly from this file.
 *  2. Declare the **TurboModule interface** (`Spec`) that Codegen uses to
 *     generate the C++ JSI bridge and the native Kotlin/Swift stubs.
 *
 * Rules enforced by the New Architecture Codegen:
 *  - All methods must have explicit return types.
 *  - `addListener` / `removeListeners` are required by `RCTEventEmitter`
 *    and must appear here verbatim.
 *  - The module name passed to `TurboModuleRegistry.getEnforcing` must match
 *    the value returned by `getName()` on both native platforms and the
 *    `codegenConfig.name` field in `package.json`.
 */

import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

// ---------------------------------------------------------------------------
// Shared domain types
// ---------------------------------------------------------------------------

/**
 * Represents a successfully decoded barcode or QR code.
 *
 * Both fields are always present when `onBarcodeScanned` fires.
 */
export type Barcode = {
  /** The raw decoded string value of the barcode. */
  data: string;
  /**
   * The format of the detected code, e.g. `'QR_CODE'`, `'EAN_13'`,
   * `'CODE_128'`. See the README for the full list of supported formats.
   */
  type: string;
};

/**
 * Payload delivered with the `onCameraError` event.
 */
export type CameraErrorEvent = {
  /** Human-readable description of what went wrong on the native side. */
  message: string;
};

// ---------------------------------------------------------------------------
// TurboModule spec
// ---------------------------------------------------------------------------

/**
 * Codegen-compatible interface for the `QrCameraPro` TurboModule.
 *
 * Matches the public surface of `QrCameraProModule` on both iOS and Android.
 * Do **not** add methods here that are not implemented natively — Codegen will
 * generate bridge code for every entry.
 */
export interface Spec extends TurboModule {
  /**
   * Starts the barcode scanning session.
   *
   * On Android, this also triggers a runtime CAMERA permission request if
   * permission has not yet been granted. On iOS, permission must be granted
   * via `NSCameraUsageDescription` / the system prompt before calling this.
   */
  startScanning(): void;

  /**
   * Stops the scanning session and releases camera resources.
   *
   * Safe to call even if scanning is not currently active.
   */
  stopScanning(): void;

  /**
   * Turns the device torch (flashlight) on or off.
   *
   * Silently ignored when no camera is bound or the device has no torch.
   *
   * @param enabled `true` to turn the torch on; `false` to turn it off.
   */
  toggleTorch(enabled: boolean): void;

  /**
   * Required by the `RCTEventEmitter` contract on both platforms.
   * Called automatically by the React Native bridge — do not call manually.
   *
   * @param eventName The name of the event being subscribed to.
   */
  addListener(eventName: string): void;

  /**
   * Required by the `RCTEventEmitter` contract on both platforms.
   * Called automatically by the React Native bridge — do not call manually.
   *
   * @param count The number of listeners being removed.
   */
  removeListeners(count: number): void;
}

/**
 * The resolved TurboModule instance.
 *
 * Uses `getEnforcing` (rather than `get`) so that a missing native registration
 * throws an explicit error at import time instead of silently returning `null`
 * and producing cryptic undefined-call errors later.
 *
 * The module name `'QrCameraPro'` must match:
 *  - `getName()` in `QrCameraProModule.kt` / `QrCameraPro.mm`
 *  - The `codegenConfig.name` field in `package.json`
 */
export default TurboModuleRegistry.getEnforcing<Spec>('QrCameraPro');
