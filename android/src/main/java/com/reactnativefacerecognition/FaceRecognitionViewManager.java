package com.reactnativefacerecognition;

import android.content.Context;
import android.view.LayoutInflater;

import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.annotations.ReactProp;

import java.util.Map;

public class FaceRecognitionViewManager extends ViewGroupManager<RecognitionView> {
  public static final String REACT_CLASS = "FaceRecognitionView";
  public static String sample = null;
  public static String EVENT_GET_RECT = "onGetRectEvent";
  public static String EVENT_GET_DATA = "onGetDataEvent";
  public static String EVENT_GET_CAPTURE = "onGetCapture";

  @Override
  public String getName() {
    return REACT_CLASS;
  }

  @Override
  protected RecognitionView createViewInstance(ThemedReactContext themedReactContext) {
    LayoutInflater inflater = (LayoutInflater)
      themedReactContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    RecognitionView view = (RecognitionView) inflater.inflate(R.layout.camera_view, null);
    return view;
  }

  @ReactProp(name = "sample")
  public void setSample(RecognitionView view, String sample) {
    FaceRecognitionViewManager.sample = sample;
  }

  @ReactProp(name = "capture")
  public void setCapture(RecognitionView view, Boolean capture) {
    if (capture) {
      view.takeImage();
    }
  }

  public Map getExportedCustomBubblingEventTypeConstants() {
    Map onGetRect = MapBuilder.of("phasedRegistrationNames", MapBuilder.of("bubbled", "onGetRect"));
    Map onGetData = MapBuilder.of("phasedRegistrationNames", MapBuilder.of("bubbled", "onGetData"));
    Map onGetCapture = MapBuilder.of("phasedRegistrationNames", MapBuilder.of("bubbled", "onGetCapture"));
    MapBuilder.Builder events = MapBuilder.builder();
    events.put(EVENT_GET_RECT, onGetRect);
    events.put(EVENT_GET_DATA, onGetData);
    events.put(EVENT_GET_CAPTURE, onGetCapture);
    return events.build();
  }
}
