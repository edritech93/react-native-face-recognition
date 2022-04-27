//
//  RecognitionView.swift
//  haermesMobile
//
//  Created by Yudi Edri Alviska on 26/11/21.
//

import UIKit
import AVFoundation
import CoreVideo
import TensorFlowLite
import MLKitFaceDetection
import MLKitVision

class RecognitionView: UIView {  
  // NOTE: Camera
  var previewView : UIView!
  var videoDataOutput: AVCaptureVideoDataOutput!
  var videoDataOutputQueue: DispatchQueue!
  var previewLayer:AVCaptureVideoPreviewLayer!
  var captureDevice : AVCaptureDevice!
  let session = AVCaptureSession()
  private var lastFrame: CMSampleBuffer?
  
  // NOTE: Detector face
  private var modelDataHandler: ModelDataHandler? = ModelDataHandler(modelFileInfo: MobileNet.modelInfo, labelsFileInfo: MobileNet.labelsInfo)
  private var isAddPending = true
  private var isLoadStorage = true
  private var isHuman = false
  
  // NOTE: listener to JS
  @objc var onGetRect: RCTBubblingEventBlock?
  @objc var onGetData: RCTBubblingEventBlock?
  @objc var onGetCapture: RCTBubblingEventBlock?
  @objc var sample: String? = nil {
    didSet {
      self.loadImageStorage()
    }
  }
  @objc var capture: Bool = false {
    didSet {
      if (capture)  {
        self.saveCaptureImage()
      }
    }
  }
  
  override init(frame: CGRect) {
    super.init(frame: frame)
    setupView()
  }
  
  required init?(coder aDecoder: NSCoder) {
    super.init(coder: aDecoder)
    setupView()
  }
  
  func setupView()  {
    let framePreview: CGRect = UIScreen.main.bounds
    previewView = UIView(frame: framePreview)
    self.addSubview(previewView)
    guard modelDataHandler != nil else {
      fatalError("Model set up failed")
    }
    self.setupAVCapture()
  }
  
  func loadImageStorage() {
    if (sample != nil) {
      let imageData = Data.init(base64Encoded: sample!, options: .init(rawValue: 0))
      if (imageData?.isEmpty != nil)   {
        let image = UIImage(data: imageData!)
        let options = FaceDetectorOptions()
        options.performanceMode = .accurate
        
        let faceDetector = FaceDetector.faceDetector(options: options)
        let visionImage = VisionImage(image: image!)
        visionImage.orientation = image!.imageOrientation
        
        faceDetector.process(visionImage) { faces, error in
          guard error == nil, let faces = faces, !faces.isEmpty else {
            return
          }
          for face in faces {
            if (face.frame.isValid())  {
              let faceFrame = face.frame
              let imageCrop = getImageFaceFromUIImage(from: image!, rectImage: faceFrame)
              self.modelDataHandler?.setImageStorage(imageStorage: imageCrop!)
              self.isLoadStorage = false
            }
          }
        }
      }
    }
  }
  
  private func detectFacesOnDevice(in image: VisionImage, width: CGFloat, height: CGFloat) {
    let options = FaceDetectorOptions()
    options.performanceMode = .fast
    options.classificationMode = .all
    let faceDetector = FaceDetector.faceDetector(options: options)
    var faces: [Face]
    do {
      faces = try faceDetector.results(in: image)
    } catch let error {
      print("Failed to detect faces with error: \(error.localizedDescription).")
      return
    }
    guard !faces.isEmpty else {
      return
    }
    weak var weakSelf = self
    DispatchQueue.main.sync {
      for face in faces {
        guard let strongSelf = weakSelf else {
          print("Self is nil!")
          return
        }
        let normalizedRect = CGRect(
          x: face.frame.origin.x / width,
          y: face.frame.origin.y / height,
          width: face.frame.size.width / width,
          height: face.frame.size.height / height
        )
        let standardizedRect = strongSelf.previewLayer.layerRectConverted(
          fromMetadataOutputRect: normalizedRect
        ).standardized
        
        let params: [String : Any] = ["x":standardizedRect.minX,
                                      "y":standardizedRect.minY,
                                      "width":standardizedRect.width,
                                      "height":standardizedRect.height]
        if ((self.onGetRect) != nil) {
          self.onGetRect!(params)
        }
      }
    }
    
    DispatchQueue.main.sync {
      for face in faces {
        if (face.frame.isValid() && isLoadStorage == false)  {
          let faceFrame = face.frame
          let imageCrop = getImageFace(from: lastFrame, rectImage: faceFrame)
          if (imageCrop != nil)  {
            var confidence: Float = 3.0
            let resultUser = modelDataHandler?.recognize(image: imageCrop!, storeExtra: isAddPending)
            let result: ModelFace = (resultUser![0])
            let extra = result.getExtra() ?? nil
            confidence = result.getDistance()!
            if (confidence < 1.0 && face.rightEyeOpenProbability <= 0.2)   {
              isHuman = true
            }
            let params: [String : Any] = ["isHuman":isHuman,
                                          "confidence":confidence]
            if ((self.onGetRect) != nil) {
              self.onGetData!(params)
            }
            let objFace = ModelFace(id: "0", title: "", distance: confidence, location: faceFrame)
            if (extra != nil)  {
              objFace.setExtra(extra: extra!)
              modelDataHandler?.register(name: "User", modelFace: objFace)
            }
            isAddPending = false
          }
        }
      }
    }
  }
  
  private func saveCaptureImage() {
    if ((self.lastFrame) != nil) {
      let image: UIImage = getImageFromBuffer(from: self.lastFrame)!
      guard let data = image.jpegData(compressionQuality: 0.1) ?? image.pngData() else {
        return
      }
      guard let directory = try? FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: false) as NSURL else {
        return
      }
      do {
        let fileName = "image_capture.png"
        let pathFile = directory.appendingPathComponent(fileName)
        try data.write(to: pathFile!)
        let output: String = directory.absoluteString! + fileName
        if ((self.onGetCapture) != nil)  {
          let params: [String : Any] = ["image":output]
          self.onGetCapture!(params)
        }
      } catch {
        print(error.localizedDescription)
      }
    }
  }
}

// AVCaptureVideoDataOutputSampleBufferDelegate protocol and related methods
extension RecognitionView:  AVCaptureVideoDataOutputSampleBufferDelegate{
  func setupAVCapture(){
    session.sessionPreset = AVCaptureSession.Preset.vga640x480
    guard let device = AVCaptureDevice
            .default(AVCaptureDevice.DeviceType.builtInWideAngleCamera,
                     for: .video,
                     position: AVCaptureDevice.Position.front) else {
              return
            }
    captureDevice = device
    beginSession()
  }
  
  func beginSession(){
    var deviceInput: AVCaptureDeviceInput!
    do {
      deviceInput = try AVCaptureDeviceInput(device: captureDevice)
      guard deviceInput != nil else {
        print("error: cant get deviceInput")
        return
      }
      
      if self.session.canAddInput(deviceInput){
        self.session.addInput(deviceInput)
      }
      
      videoDataOutput = AVCaptureVideoDataOutput()
      videoDataOutput.alwaysDiscardsLateVideoFrames=true
      videoDataOutputQueue = DispatchQueue(label: "VideoDataOutputQueue")
      videoDataOutput.setSampleBufferDelegate(self, queue:self.videoDataOutputQueue)
      
      if session.canAddOutput(self.videoDataOutput){
        session.addOutput(self.videoDataOutput)
      }
      
      videoDataOutput.connection(with: .video)?.isEnabled = true
      
      previewLayer = AVCaptureVideoPreviewLayer(session: self.session)
      previewLayer.videoGravity = AVLayerVideoGravity.resizeAspectFill
      
      let rootLayer :CALayer = self.previewView.layer
      rootLayer.masksToBounds=true
      previewLayer.frame = rootLayer.bounds
      rootLayer.addSublayer(self.previewLayer)
      session.startRunning()
    } catch let error as NSError {
      deviceInput = nil
      print("error: \(error.localizedDescription)")
    }
  }
  
  func captureOutput(
    _ output: AVCaptureOutput,
    didOutput sampleBuffer: CMSampleBuffer,
    from connection: AVCaptureConnection
  ) {
    guard let imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
      print("Failed to get image buffer from sample buffer.")
      return
    }
    lastFrame = sampleBuffer
    let visionImage = VisionImage(buffer: sampleBuffer)
    
    let imageWidth = CGFloat(CVPixelBufferGetWidth(imageBuffer))
    let imageHeight = CGFloat(CVPixelBufferGetHeight(imageBuffer))
    detectFacesOnDevice(in: visionImage, width: imageWidth, height: imageHeight)
  }
  
  // NOTE: stop AVCapture
  func stopCamera(){
    session.stopRunning()
  }
}
