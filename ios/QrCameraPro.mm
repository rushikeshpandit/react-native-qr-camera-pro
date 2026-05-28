#import "QrCameraPro.h"
#import <AVFoundation/AVFoundation.h>

#if __has_include("QrCameraPro-Swift.h")
#import "QrCameraPro-Swift.h"
#else
#import <QrCameraPro/QrCameraPro-Swift.h>
#endif

@implementation QrCameraPro

- (void)startScanning {
    [QrCameraProSwift.shared setEventEmitter:self];
    [QrCameraProSwift.shared startScanning];
}

- (void)stopScanning {
    [QrCameraProSwift.shared stopScanning];
}

- (void)toggleTorch:(BOOL)enabled {
    [QrCameraProSwift.shared toggleTorch:enabled];
}

// FIX 1: Call [super addListener:] so RCTEventEmitter's internal listenerCount
// is incremented. Without this it stays at 0 forever, making every sendEvent()
// log "Sending `X` with no listeners registered."
// RCTEventEmitter also calls startObserving() internally when count goes 0→1.
- (void)addListener:(NSString *)eventName {
    [super addListener:eventName];
}

// FIX 1 (cont.): Same for removeListeners — must decrement the counter so
// RCTEventEmitter can call stopObserving() when count reaches 0.
- (void)removeListeners:(double)count {
    [super removeListeners:count];
}

// FIX 2: startObserving is called by RCTEventEmitter when the listener count
// goes from 0 → 1 (first subscriber). Tell the Swift singleton it can now
// safely emit events. This closes the timing gap between startScanning() and
// the JS useEffect registering its listener.
- (void)startObserving {
    [QrCameraProSwift.shared setHasListeners:YES];
}

// FIX 2 (cont.): stopObserving is called when count drops back to 0.
// Stop emitting so we don't log warnings after the component unmounts.
- (void)stopObserving {
    [QrCameraProSwift.shared setHasListeners:NO];
}

- (NSArray<NSString *> *)supportedEvents {
    return @[@"onBarcodeScanned", @"onCameraError"];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeQrCameraProSpecJSI>(params);
}

+ (NSString *)moduleName
{
    return @"QrCameraPro";
}

@end