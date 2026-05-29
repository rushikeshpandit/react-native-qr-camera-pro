// QrCameraProViewManager.m
// Objective-C shim that exposes QrCameraProViewManager (Swift) to React Native's
// module registry and declares the view properties available from JavaScript.
//
// The `onBarcodeScanned` direct event block allows JS consumers to pass a
// callback prop directly to the <QrCameraProView> component (Old Architecture
// path). In the New Architecture this is superseded by the TurboModule event
// emitter, but the prop is kept for backwards compatibility.

#import <React/RCTViewManager.h>
#import <React/RCTUIManager.h>

/// Registers QrCameraProViewManager with the React Native bridge and
/// exposes the `onBarcodeScanned` direct-event callback as a view property.
@interface RCT_EXTERN_MODULE(QrCameraProViewManager, RCTViewManager)

/// Prop that accepts a JS callback invoked when a barcode is scanned.
/// Declared as a `RCTDirectEventBlock` so React Native's event system routes
/// calls from the native side back to the JS handler automatically.
RCT_EXPORT_VIEW_PROPERTY(onBarcodeScanned, RCTDirectEventBlock)

@end