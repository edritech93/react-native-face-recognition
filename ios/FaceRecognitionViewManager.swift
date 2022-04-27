import Foundation

@objc (FaceRecognitionViewManager)
class FaceRecognitionViewManager: RCTViewManager {
    
    override static func requiresMainQueueSetup() -> Bool {
        return true
    }
    
    override func view() -> (UIView) {
        return RecognitionView()
    }
}

