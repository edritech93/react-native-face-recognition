package com.reactnativefacerecognition.recognition;

import android.graphics.RectF;

public class ModelFace {
  private final String id;
  private final String title;
  private final Float distance;
  private Object extra;
  private RectF location;
  private Integer color;

  public ModelFace(final String id, final String title, final Float distance, final RectF location) {
    this.id = id;
    this.title = title;
    this.distance = distance;
    this.location = location;
  }

  public String getTitle() {
    return title;
  }

  public Float getDistance() {
    return distance;
  }

  public Object getExtra() {
    return extra;
  }

  public void setExtra(Object extra) {
    this.extra = extra;
  }

  public RectF getLocation() {
    return location;
  }

  public void setLocation(RectF location) {
    this.location = location;
  }

  public Integer getColor() {
    return color;
  }

  public void setColor(Integer color) {
    this.color = color;
  }

  @Override
  public String toString() {
    return "ModelRecognition{" +
      "id='" + id + '\'' +
      ", title='" + title + '\'' +
      ", distance=" + distance +
      ", extra=" + extra +
      ", location=" + location +
      ", color=" + color +
      '}';
  }
}
