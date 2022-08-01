package com.reactnativefacerecognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;
import com.otaliastudios.cameraview.size.Size;
import com.reactnativefacerecognition.recognition.HelperFace;
import com.reactnativefacerecognition.recognition.InterfaceValidation;
import com.reactnativefacerecognition.recognition.ModelFace;
import com.reactnativefacerecognition.recognition.ValidationFace;
import com.reactnativefacerecognition.utils.ImageUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

public class RecognitionView extends CameraView implements FrameProcessor {
  private final String TAG = "RecognitionView";
  private static final int TF_OD_API_INPUT_SIZE = 112;
  private static final boolean TF_OD_API_IS_QUANTIZED = false;
  private static final String TF_OD_API_MODEL_FILE = "mobile_face_net.tflite";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
  private final Context context;

  // NOTE: Recognition
  private FaceDetector faceDetector;
  private InterfaceValidation detector;
  private boolean isAddPending = true;
  private Bitmap bmpResult = null;
  private final Bitmap bmpFace = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Bitmap.Config.ARGB_8888);
  private boolean isHuman = false;

  private ImageView imageFace;

  public RecognitionView(@NonNull Context context) {
    super(context);
    this.context = context;
    initialize();
  }

  public RecognitionView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
    initialize();
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    imageFace = RecognitionView.this.findViewById(R.id.image_face);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    this.destroy();
  }

  private void initialize() {
    this.open();
    try {
      FaceDetectorOptions options =
        new FaceDetectorOptions.Builder()
          .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
          .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
          .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
          .enableTracking()
          .build();
      faceDetector = FaceDetection.getClient(options);
    } catch (Exception e) {
      e.printStackTrace();
    }
    try {
      detector =
        ValidationFace.create(
          getContext().getAssets(),
          TF_OD_API_MODEL_FILE,
          TF_OD_API_LABELS_FILE,
          TF_OD_API_INPUT_SIZE,
          TF_OD_API_IS_QUANTIZED);
      this.addFrameProcessor(this);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void process(@NonNull Frame frame) {
    try {
      if (frame.getDataClass() == byte[].class) {
        byte[] data = frame.getData();
        Size s = frame.getSize();
        YuvImage yuv = new YuvImage(data, ImageFormat.NV21, s.getWidth(), s.getHeight(), null);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, s.getWidth(), s.getHeight()), 100, stream);
        byte[] buf = stream.toByteArray();
        Bitmap originalBitmap = BitmapFactory.decodeByteArray(buf, 0, buf.length, null);
        Matrix matrix = new Matrix();
        matrix.postRotate(270);
        bmpResult = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, true);
      } else if (frame.getDataClass() == Image.class) {
        Image data = frame.getData();
        ByteBuffer buffer = data.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        bmpResult = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
      }
      if (bmpResult != null) {
        try {
          InputImage image = InputImage.fromBitmap(bmpResult, 0);
          faceDetector
            .process(image)
            .addOnSuccessListener(faces -> {
              onFaceDetection(faces);
            });
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void onFaceDetection(List<Face> faces) {
    AsyncTask.execute(() -> {
      for (Face face : faces) {
        if (imageFace != null && bmpFace != null) {
          // NOTE: for testing only
          // this.post(() -> imageFace.setImageBitmap(bmpFace));
        }
        try {
          final RectF faceBB = new RectF(face.getBoundingBox());
          final Canvas cvFace = new Canvas(bmpFace);
          float sx = ((float) TF_OD_API_INPUT_SIZE) / faceBB.width();
          float sy = ((float) TF_OD_API_INPUT_SIZE) / faceBB.height();
          Matrix matrix = new Matrix();
          matrix.postTranslate(-faceBB.left, -faceBB.top);
          matrix.postScale(sx, sy);
          cvFace.drawBitmap(bmpResult, matrix, null);

          int resWidth = ImageUtils.pxToDp(context, (int) faceBB.width());
          int resHeight = ImageUtils.pxToDp(context, (int) faceBB.height());
          int resX = ImageUtils.pxToDp(context, (int) faceBB.left);
          int resY = ImageUtils.pxToDp(context, (int) faceBB.top);
          WritableMap event = Arguments.createMap();
          event.putDouble("x", resX);
          event.putDouble("y", resY);
          event.putDouble("width", resWidth);
          event.putDouble("height", resHeight);
          ReactContext reactContext = (ReactContext) this.getContext();
          reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(this.getId(), FaceRecognitionViewManager.EVENT_GET_RECT, event);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
    AsyncTask.execute(() -> {
      for (Face face : faces) {
        try {
          Object extra = null;
          float confidence = 3.0F;
          List<ModelFace> modelFaceList = detector.recognizeImage(bmpFace, isAddPending);
          if (modelFaceList.size() > 0) {
            ModelFace result = modelFaceList.get(0);
            extra = result.getExtra();
            confidence = result.getDistance();
            Log.e(TAG, "confidence=" + confidence);
            if (confidence < 1.0f && face.getRightEyeOpenProbability() != null) {
              float eyeOpenProbability = face.getRightEyeOpenProbability();
              if (eyeOpenProbability <= 0.5) {
                isHuman = true;
              }
            }
            WritableMap event = Arguments.createMap();
            event.putDouble("confidence", confidence);
            event.putBoolean("isHuman", isHuman);
            ReactContext reactContext = (ReactContext) this.getContext();
            reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(this.getId(), FaceRecognitionViewManager.EVENT_GET_DATA, event);
          }
          final ModelFace objFace = new ModelFace("0", "User", confidence, null);
          objFace.setExtra(extra);
          if (extra != null) {
            detector.register("User", objFace);
            isAddPending = false;
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }

  public void takeImage() {
    try {
      Bitmap bitmapResult = Bitmap.createBitmap(bmpResult);
      HelperFace helperFace = new HelperFace();
      Uri resultUri = helperFace.saveBitmapToStorage(this.getContext(), bitmapResult, "recognition_capture");
      WritableMap event = Arguments.createMap();
      event.putString("image", String.valueOf(resultUri));
      ReactContext reactContext = (ReactContext) this.getContext();
      reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(this.getId(), FaceRecognitionViewManager.EVENT_GET_CAPTURE, event);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
