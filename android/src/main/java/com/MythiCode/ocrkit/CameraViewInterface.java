package com.MythiCode.ocrkit;

import android.widget.RelativeLayout;

import io.flutter.plugin.common.MethodChannel;

public interface CameraViewInterface {

    void initCamera(RelativeLayout relativeLayout, boolean hasBarcodeReader, char flashMode
            , boolean fillScale, boolean isTakePictureMode, boolean isScanningText);

    void setCameraVisible(boolean isCameraVisible);

    void changeFlashMode(char captureFlashMode);

    void takePicture(final MethodChannel.Result result);

    void pauseCamera();

    void resumeCamera();

    void dispose();

    void setScanForText(boolean isScanningText);

    void processImageFromPath(String path);

    void processImageFromPathWithoutView(String path);
}
