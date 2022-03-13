package com.MythiCode.ocrkit.Model;

import android.graphics.Point;

import java.util.ArrayList;

public class ValueModel {
    public String text;
    public Point[] cornerPointModel;

    public ValueModel(String text, Point[] cornerPointModel) {
        this.text = text;
        this.cornerPointModel = cornerPointModel;
    }
}
