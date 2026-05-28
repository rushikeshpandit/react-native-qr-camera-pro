#import <React/RCTViewManager.h>
#import <React/RCTUIManager.h>

@interface RCT_EXTERN_MODULE(QrCameraProViewManager, RCTViewManager)

RCT_EXPORT_VIEW_PROPERTY(onBarcodeScanned, RCTDirectEventBlock)

@end
