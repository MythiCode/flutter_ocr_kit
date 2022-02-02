package com.MythiCode.ocrkit;

import android.media.Image;
import android.media.ImageReader;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import io.flutter.plugin.common.MethodChannel;

@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class TakePictureImageListener implements ImageReader.OnImageAvailableListener {


    private FlutterMethodListener flutterMethodListener;
    private MethodChannel.Result resultMethodChannel;
    private File file;

    public TakePictureImageListener(FlutterMethodListener flutterMethodListener, MethodChannel.Result resultMethodChannel, File file) {
        this.flutterMethodListener = flutterMethodListener;
        this.resultMethodChannel = resultMethodChannel;
        this.file = file;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        try (Image image = reader.acquireLatestImage()) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
            save(bytes);
            flutterMethodListener.onTakePicture(resultMethodChannel, file + "");
        } catch (IOException e) {
            e.printStackTrace();
            flutterMethodListener.onTakePictureFailed(resultMethodChannel, "-101", "Capture Failed");
        }
    }


    private void save(byte[] bytes) throws IOException {
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
    }
}
