import CoreImage
import TensorFlowLite
import UIKit
import Accelerate
import AVFoundation
import CoreMedia
import CoreVideo

/// Information about a model file or labels file.
typealias FileInfo = (name: String, extension: String)

/// Information about the MobileNet model.
enum MobileNet {
    static let modelInfo: FileInfo = (name: "mobile_face_net", extension: "tflite")
    static let labelsInfo: FileInfo = (name: "labelmap", extension: "txt")
}

/// This class handles all data preprocessing and makes calls to run inference on a given frame
/// by invoking the `Interpreter`. It then formats the inferences obtained and returns the top N
/// results for a successful inference.
class ModelDataHandler {
    
    // MARK: - Internal Properties
    
    /// The current thread count used by the TensorFlow Lite Interpreter.
    let threadCount: Int
    
    // MARK: - Model Parameters
    let batchSize = 1
    let inputChannels = 3
    let inputWidth = 112
    let inputHeight = 112
    
    // MARK: - Private Properties
    
    /// List of labels from the given labels file.
    private var labels: [String] = []
    
    /// TensorFlow Lite `Interpreter` object for performing inference on a given model.
    private var interpreter: Interpreter
    private var registered = [String : ModelFace]()
    private var imageStorage: UIImage?
    
    // MARK: - Initialization
    
    /// A failable initializer for `ModelDataHandler`. A new instance is created if the model and
    /// labels files are successfully loaded from the app's main bundle. Default `threadCount` is 1.
    init?(modelFileInfo: FileInfo, labelsFileInfo: FileInfo, threadCount: Int = 1) {
        let modelFilename = modelFileInfo.name
        
        // Construct the path to the model file.
        guard let modelPath = Bundle.main.path(
            forResource: modelFilename,
            ofType: modelFileInfo.extension
        ) else {
            print("Failed to load the model file with name: \(modelFilename).")
            return nil
        }
        
        // Specify the options for the `Interpreter`.
        self.threadCount = threadCount
        var options = Interpreter.Options()
        options.threadCount = threadCount
        do {
            // Create the `Interpreter`.
            interpreter = try Interpreter(modelPath: modelPath, options: options)
            // Allocate memory for the model's input `Tensor`s.
            try interpreter.allocateTensors()
        } catch let error {
            print("Failed to create the interpreter with error: \(error.localizedDescription)")
            return nil
        }
        // Load the classes listed in the labels file.
        loadLabels(fileInfo: labelsFileInfo)
    }
    
    func register(name: String, modelFace: ModelFace) {
        registered[name] = modelFace
    }
    
    func findNearest(emb: Tensor) -> [String : Float] {
        var ret = [String : Float]()
        var distance: Float = 0.0;
        var name: String = ""
        for itemRegistered in registered {
            name = itemRegistered.key
            let knownEmb = itemRegistered.value.getExtra()
            
            let newKnownEmb: Tensor = knownEmb!
            let resultsKnown: [Float] = [Float32](unsafeData: newKnownEmb.data) ?? []
            let resultsEmb: [Float] = [Float32](unsafeData: emb.data) ?? []
            
            for i in 1...resultsEmb.count {
                let index = i - 1
                let diff: Float = resultsEmb[index] - resultsKnown[index]
                let diffCalc = diff * diff
                distance = distance + diffCalc
            }
        }
        ret[name] = distance
        return ret
    }
    
    func recognize(image: UIImage, storeExtra: Bool) -> [ModelFace] {
        var outputTensor: Tensor?
        if (storeExtra) {
            outputTensor = tensorCamera(image: imageStorage!)
        } else {
            outputTensor = tensorCamera(image: image)
        }
        
        var distance: Float = 0.0
        let id = "0"
        var label = "?"
        
        if (registered.count > 0)   {
            let nearest: [String : Float] = findNearest(emb: outputTensor!)
            if (!nearest.isEmpty) {
                for item in nearest {
                    label = item.key
                    distance = item.value
                }
            }
        }
        var arrayFace = [ModelFace]()
        let regLocation = CGRect(x: 0, y: 0, width: 200, height: 200)
        let modelFace: ModelFace = ModelFace(id: id, title: label, distance: distance, location: regLocation)
        if (storeExtra) {
            modelFace.setExtra(extra: outputTensor!);
        }
        arrayFace.append(modelFace)
        return arrayFace
    }
    
    func tensorCamera(image: UIImage) -> Tensor? {
        let pixelBuffer = uiImageToPixelBuffer(image: image, size: inputWidth)
        if (pixelBuffer != nil) {
            let outputTensor: Tensor
            do {
                let inputTensor = try interpreter.input(at: 0)
                // Remove the alpha component from the image buffer to get the RGB data.
                guard let rgbData = rgbDataFromBuffer(
                    pixelBuffer!,
                    byteCount: batchSize * inputWidth * inputHeight * inputChannels,
                    isModelQuantized: inputTensor.dataType == .uInt8
                ) else {
                    print("Failed to convert the image buffer to RGB data.")
                    return nil
                }
                // Copy the RGB data to the input `Tensor`.
                try interpreter.copy(rgbData, toInputAt: 0)
                
                // Run inference by invoking the `Interpreter`.
                try interpreter.invoke()
                // Get the output `Tensor` to process the inference results.
                outputTensor = try interpreter.output(at: 0)
                return outputTensor
            } catch let error {
                print("Failed to invoke the interpreter with error: \(error.localizedDescription)")
                return nil
            }
        } else {
            return nil
        }
    }
    
    func setImageStorage(imageStorage: UIImage) {
        self.imageStorage = imageStorage
    }
    
    /// Loads the labels from the labels file and stores them in the `labels` property.
    private func loadLabels(fileInfo: FileInfo) {
        let filename = fileInfo.name
        let fileExtension = fileInfo.extension
        guard let fileURL = Bundle.main.url(forResource: filename, withExtension: fileExtension) else {
            fatalError("Labels file not found in bundle. Please add a labels file with name " +
                        "\(filename).\(fileExtension) and try again.")
        }
        do {
            let contents = try String(contentsOf: fileURL, encoding: .utf8)
            labels = contents.components(separatedBy: .newlines)
        } catch {
            fatalError("Labels file named \(filename).\(fileExtension) cannot be read. Please add a " +
                        "valid labels file and try again.")
        }
    }
    
    /// Returns the RGB data representation of the given image buffer with the specified `byteCount`.
    ///
    /// - Parameters
    ///   - buffer: The pixel buffer to convert to RGB data.
    ///   - byteCount: The expected byte count for the RGB data calculated using the values that the
    ///       model was trained on: `batchSize * imageWidth * imageHeight * componentsCount`.
    ///   - isModelQuantized: Whether the model is quantized (i.e. fixed point values rather than
    ///       floating point values).
    /// - Returns: The RGB data representation of the image buffer or `nil` if the buffer could not be
    ///     converted.
    private func rgbDataFromBuffer(
        _ buffer: CVPixelBuffer,
        byteCount: Int,
        isModelQuantized: Bool
    ) -> Data? {
        CVPixelBufferLockBaseAddress(buffer, .readOnly)
        defer {
            CVPixelBufferUnlockBaseAddress(buffer, .readOnly)
        }
        guard let sourceData = CVPixelBufferGetBaseAddress(buffer) else {
            return nil
        }
        
        let width = CVPixelBufferGetWidth(buffer)
        let height = CVPixelBufferGetHeight(buffer)
        let sourceBytesPerRow = CVPixelBufferGetBytesPerRow(buffer)
        let destinationChannelCount = 3
        let destinationBytesPerRow = destinationChannelCount * width
        
        var sourceBuffer = vImage_Buffer(data: sourceData,
                                         height: vImagePixelCount(height),
                                         width: vImagePixelCount(width),
                                         rowBytes: sourceBytesPerRow)
        
        guard let destinationData = malloc(height * destinationBytesPerRow) else {
            print("Error: out of memory")
            return nil
        }
        
        defer {
            free(destinationData)
        }
        
        var destinationBuffer = vImage_Buffer(data: destinationData,
                                              height: vImagePixelCount(height),
                                              width: vImagePixelCount(width),
                                              rowBytes: destinationBytesPerRow)
        
        let pixelBufferFormat = CVPixelBufferGetPixelFormatType(buffer)
        
        switch (pixelBufferFormat) {
        case kCVPixelFormatType_32BGRA:
            vImageConvert_BGRA8888toRGB888(&sourceBuffer, &destinationBuffer, UInt32(kvImageNoFlags))
        case kCVPixelFormatType_32ARGB:
            vImageConvert_ARGB8888toRGB888(&sourceBuffer, &destinationBuffer, UInt32(kvImageNoFlags))
        case kCVPixelFormatType_32RGBA:
            vImageConvert_RGBA8888toRGB888(&sourceBuffer, &destinationBuffer, UInt32(kvImageNoFlags))
        default:
            // Unknown pixel format.
            return nil
        }
        
        let byteData = Data(bytes: destinationBuffer.data, count: destinationBuffer.rowBytes * height)
        if isModelQuantized {
            return byteData
        }
        
        // Not quantized, convert to floats
        let bytes = Array<UInt8>(unsafeData: byteData)!
        var floats = [Float]()
        for i in 0..<bytes.count {
            floats.append(Float(bytes[i]) / 255.0)
        }
        return Data(copyingBufferOf: floats)
    }
}

// MARK: - Extensions

extension Data {
    /// Creates a new buffer by copying the buffer pointer of the given array.
    ///
    /// - Warning: The given array's element type `T` must be trivial in that it can be copied bit
    ///     for bit with no indirection or reference-counting operations; otherwise, reinterpreting
    ///     data from the resulting buffer has undefined behavior.
    /// - Parameter array: An array with elements of type `T`.
    init<T>(copyingBufferOf array: [T]) {
        self = array.withUnsafeBufferPointer(Data.init)
    }
}

extension Array {
    /// Creates a new array from the bytes of the given unsafe data.
    ///
    /// - Warning: The array's `Element` type must be trivial in that it can be copied bit for bit
    ///     with no indirection or reference-counting operations; otherwise, copying the raw bytes in
    ///     the `unsafeData`'s buffer to a new array returns an unsafe copy.
    /// - Note: Returns `nil` if `unsafeData.count` is not a multiple of
    ///     `MemoryLayout<Element>.stride`.
    /// - Parameter unsafeData: The data containing the bytes to turn into an array.
    init?(unsafeData: Data) {
        guard unsafeData.count % MemoryLayout<Element>.stride == 0 else { return nil }
        #if swift(>=5.0)
        self = unsafeData.withUnsafeBytes { .init($0.bindMemory(to: Element.self)) }
        #else
        self = unsafeData.withUnsafeBytes {
            .init(UnsafeBufferPointer<Element>(
                start: $0,
                count: unsafeData.count / MemoryLayout<Element>.stride
            ))
        }
        #endif  // swift(>=5.0)
    }
}
