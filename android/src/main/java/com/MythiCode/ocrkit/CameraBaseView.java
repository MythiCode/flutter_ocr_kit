package com.MythiCode.ocrkit;

import android.app.Activity;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.platform.PlatformView;

public class CameraBaseView implements PlatformView {
    private final Activity activity;
    FlutterMethodListener flutterMethodListener;
    private final RelativeLayout relativeLayout;
    private CameraViewInterface cameraViewInterface;

    public CameraBaseView(Activity activity, FlutterMethodListener flutterMethodListener) {
        this.activity = activity;
        this.flutterMethodListener = flutterMethodListener;
        relativeLayout = new RelativeLayout(activity);
        relativeLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        relativeLayout.setBackgroundColor(Color.parseColor("#000000"));
    }

    public void initCamera(boolean hasTextReader, char flashMode, boolean isFillScale, boolean isTakePictureMode, boolean isScanningText) {
//        if(hasTextReader && !useCamera2API){
//            throw new RuntimeException("You cannot use barcode reader for reading barcode");
//        }

        Log.e("CameraBaseView", "initCamera:isTakePictureMode " + isTakePictureMode);
        cameraViewInterface = new CameraViewX(activity, flutterMethodListener);

//        if (isTakePictureMode)
//     
//        else cameraViewInterface = new CameraView2(activity, flutterMethodListener);

        cameraViewInterface.initCamera(relativeLayout, hasTextReader, flashMode, isFillScale, isTakePictureMode, isScanningText);
    }

    public void setCameraVisible(boolean isCameraVisible) {
        if (cameraViewInterface != null)
            cameraViewInterface.setCameraVisible(isCameraVisible);
    }

    public void setScanForText(boolean isScanningText) {
        cameraViewInterface.setScanForText(isScanningText);
    }

    public void changeFlashMode(char captureFlashMode) {
        cameraViewInterface.changeFlashMode(captureFlashMode);
    }

    public void processImageFromPath(String path) {
        cameraViewInterface.processImageFromPath(path);
    }

    public void takePicture(final MethodChannel.Result result) {
        cameraViewInterface.takePicture(result);
    }

    public void pauseCamera() {
        cameraViewInterface.pauseCamera();
    }

    public void resumeCamera() {
        cameraViewInterface.resumeCamera();
    }

    @Override
    public View getView() {
        return relativeLayout;
    }

    @Override
    public void dispose() {
        cameraViewInterface.dispose();
    }

}
