package com.MythiCode.ocrkit;

import io.flutter.plugin.common.MethodChannel;

public interface FlutterMethodListener {

    void onTextRead(String textText, String s, String text);

    void onTakePicture(MethodChannel.Result result, String filePath);

    void onTakePictureFailed(MethodChannel.Result result, String errorCode, String errorMessage);
}
