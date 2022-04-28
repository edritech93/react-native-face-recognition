package com.reactnativefacerecognition.recognition;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Trace;
import android.util.Base64;
import android.util.Pair;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.reactnativefacerecognition.FaceRecognitionViewManager;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class ValidationFace implements InterfaceValidation {
  private final String TAG = "TagValidationFace";
  private static final int OUTPUT_SIZE = 192;
  private static final float IMAGE_MEAN = 128.0f;
  private static final float IMAGE_STD = 128.0f;
  private final Vector<String> labels = new Vector<>();
  private final HashMap<String, ModelFace> registered = new HashMap<>();
  private float[][] outputFloat = new float[1][OUTPUT_SIZE];
  private ByteBuffer imgData;
  private Interpreter interpreter;
  private boolean isModelQuantized;
  private int inputSize;
  private int[] intValues;

  public static InterfaceValidation create(
    final AssetManager assetManager,
    final String modelFilename,
    final String labelFilename,
    final int inputSize,
    final boolean isQuantized)
    throws IOException {
    final ValidationFace validationFace = new ValidationFace();
    String actualFilename = labelFilename.split("file:///android_asset/")[1];
    InputStream labelsInput = assetManager.open(actualFilename);
    BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
    String line;
    while ((line = br.readLine()) != null) {
      validationFace.labels.add(line);
    }
    br.close();
    validationFace.inputSize = inputSize;
    try {
      validationFace.interpreter = new Interpreter(loadModelFile(assetManager, modelFilename));
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
    validationFace.isModelQuantized = isQuantized;
    int numBytesPerChannel;
    if (isQuantized) {
      numBytesPerChannel = 1; // Quantized
    } else {
      numBytesPerChannel = 4; // Floating point
    }
    validationFace.imgData = ByteBuffer.allocateDirect(1 * validationFace.inputSize * validationFace.inputSize * 3 * numBytesPerChannel);
    validationFace.imgData.order(ByteOrder.nativeOrder());
    validationFace.intValues = new int[validationFace.inputSize * validationFace.inputSize];
    return validationFace;
  }

  public void register(String name, ModelFace modelFace) {
    registered.put(name, modelFace);
  }

  private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
    throws IOException {
    AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  private Pair<String, Float> findNearest(float[] emb) {
    Pair<String, Float> ret = null;
    for (Map.Entry<String, ModelFace> entry : registered.entrySet()) {
      final String name = entry.getKey();
      final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];
      float distance = 0;
      for (int i = 0; i < emb.length; i++) {
        float diff = emb[i] - knownEmb[i];
        distance += diff * diff;
      }
      if (ret == null || distance < ret.second) {
        ret = new Pair<>(name, distance);
      }
    }
    return ret;
  }

  @Override
  public List<ModelFace> recognizeImage(final Bitmap bitmap, boolean storeExtra) {
    outputFloat = new float[1][OUTPUT_SIZE];
    if (!storeExtra) {
      tensorCamera(bitmap);
    } else {
      tensorImageStorage();
    }
    float distance = Float.MAX_VALUE;
    String id = "0";
    String label = "?";
    if (registered.size() > 0) {
      final Pair<String, Float> nearest = findNearest(outputFloat[0]);
      if (nearest != null) {
        label = nearest.first;
        distance = nearest.second;
      }
    }
    final int numDetectionsOutput = 1;
    final ArrayList<ModelFace> modelFaces = new ArrayList<>(numDetectionsOutput);
    ModelFace rec = new ModelFace(
      id,
      label,
      distance,
      new RectF());
    modelFaces.add(rec);
    if (storeExtra) {
      rec.setExtra(outputFloat);
    }
    return modelFaces;
  }

  private void tensorCamera(Bitmap bitmap) {
    if (bitmap != null) {
      try {
        Trace.beginSection("recognizeImage");
        Trace.beginSection("preprocessBitmap");
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        imgData.rewind();
        for (int i = 0; i < inputSize; ++i) {
          for (int j = 0; j < inputSize; ++j) {
            int pixelValue = intValues[i * inputSize + j];
            if (isModelQuantized) {
              imgData.put((byte) ((pixelValue >> 16) & 0xFF));
              imgData.put((byte) ((pixelValue >> 8) & 0xFF));
              imgData.put((byte) (pixelValue & 0xFF));
            } else {
              imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
              imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
              imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
          }
        }
        Trace.endSection();
        Trace.beginSection("feed");
        Object[] inputArray = {imgData};
        Trace.endSection();
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputFloat);
        Trace.beginSection("run");
        interpreter.runForMultipleInputsOutputs(inputArray, outputMap);
        Trace.endSection();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private void tensorImageStorage() {
    if (FaceRecognitionViewManager.sample != null) {
      try {
        byte[] decodedString = Base64.decode(FaceRecognitionViewManager.sample, Base64.DEFAULT);
        Bitmap bitmapStorage = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        FaceDetectorOptions faceDetectorOptions =
          new FaceDetectorOptions.Builder()
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build();
        FaceDetector imageDetector = FaceDetection.getClient(faceDetectorOptions);
        InputImage image = InputImage.fromBitmap(bitmapStorage, 0);
        imageDetector
          .process(image)
          .addOnSuccessListener(faces -> {
            if (faces.size() > 0) {
              try {
                Face face = faces.get(0);
                final RectF boundingBox = new RectF(face.getBoundingBox());
                RectF faceBB = new RectF(boundingBox);
                Bitmap crop = Bitmap.createBitmap(bitmapStorage,
                  (int) faceBB.left,
                  (int) faceBB.top,
                  (int) faceBB.width(),
                  (int) faceBB.height());
                HelperFace helperFace = new HelperFace();
                ByteBuffer byteBuffer = helperFace.convertBitmapToBuffer(crop);

                Trace.beginSection("feed");
                Object[] inputArray = {byteBuffer};
                Trace.endSection();

                Map<Integer, Object> outputMap = new HashMap<>();
                outputMap.put(0, outputFloat);

                Trace.beginSection("run");
                interpreter.runForMultipleInputsOutputs(inputArray, outputMap);
                Trace.endSection();
                imageDetector.close();
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          });
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
