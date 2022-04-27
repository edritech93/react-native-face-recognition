#import "React/RCTViewManager.h"

@interface RCT_EXTERN_MODULE(FaceRecognitionViewManager, RCTViewManager)
RCT_EXPORT_VIEW_PROPERTY(onGetData, RCTBubblingEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onGetRect, RCTBubblingEventBlock)
RCT_EXPORT_VIEW_PROPERTY(onGetCapture, RCTBubblingEventBlock)
RCT_EXPORT_VIEW_PROPERTY(sample, NSString)
RCT_EXPORT_VIEW_PROPERTY(capture, BOOL)

@end
