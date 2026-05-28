import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export type Barcode = {
  data: string;
  type: string; // e.g., 'QR_CODE', 'EAN_13', etc.
};

export interface Spec extends TurboModule {
  startScanning(): void;
  stopScanning(): void;
  toggleTorch(enabled: boolean): void;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

// The module name 'QrCameraPro' must match the name used in `codegenConfig.name`
// in `package.json` for the TurboModule, after dropping the 'Spec' suffix.
export default TurboModuleRegistry.get<Spec>('QrCameraPro');
