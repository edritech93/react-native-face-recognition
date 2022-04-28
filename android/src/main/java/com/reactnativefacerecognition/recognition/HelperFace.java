package com.reactnativefacerecognition.recognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;

import androidx.annotation.NonNull;

import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class HelperFace {
  private final int IMAGE_SIZE = 112;

  private final ImageProcessor imageTensorProcessor = new ImageProcessor.Builder()
    .add(new ResizeOp(IMAGE_SIZE, IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
    .add(new NormalizeOp(127.5f, 127.5f))
    .build();

  public ByteBuffer convertBitmapToBuffer(Bitmap bitmap) {
    TensorImage imageTensor = imageTensorProcessor.process(TensorImage.fromBitmap(bitmap));
    return imageTensor.getBuffer();
  }

  public String convertBitmapToBase64(Bitmap bitmap) {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
    byte[] byteArray = byteArrayOutputStream.toByteArray();
    return Base64.encodeToString(byteArray, Base64.DEFAULT);
  }

  public Uri saveBitmapToStorage(Context context, Bitmap bitmap, @NonNull String name) throws IOException {
    String FOLDER_NAME = "HAERMES";
    OutputStream fos;
    Uri resultUri;

    // TODO: need check and fix Android > 10 issue save image
    // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    //     ContentResolver resolver = context.getContentResolver();
    //     ContentValues contentValues = new ContentValues();
    //     contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
    //     contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
    //     contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + FOLDER_NAME);
    //     Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
    //     fos = resolver.openOutputStream(imageUri);
    //     resultUri = imageUri;
    // } else {
    String imagesDir = Environment.getExternalStoragePublicDirectory(
      Environment.DIRECTORY_DCIM).toString() + File.separator + FOLDER_NAME;
    File file = new File(imagesDir);
    if (!file.exists()) {
      file.mkdir();
    }
    File image = new File(imagesDir, name + ".png");
    fos = new FileOutputStream(image);
    resultUri = Uri.fromFile(image);
    // }
    bitmap.compress(Bitmap.CompressFormat.PNG, 50, fos);
    fos.flush();
    fos.close();
    return resultUri;
  }
}
