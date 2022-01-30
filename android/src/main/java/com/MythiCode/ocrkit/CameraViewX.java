package com.MythiCode.ocrkit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.MythiCode.ocrkit.Model.CornerPointModel;
import com.MythiCode.ocrkit.Model.LineModel;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import io.flutter.plugin.common.MethodChannel;

import static android.content.ContentValues.TAG;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraViewX implements CameraViewInterface {

    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private final Activity activity;
    private final FlutterMethodListener flutterMethodListener;
    private ImageCapture imageCapture;
    private char previewFlashMode;
    private boolean isTakePictureMode;
    private boolean isScanningText;
    private int userCameraSelector;
    private Point displaySize;
    private TextRecognizer textRecognizer;
    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageAnalysis imageAnalyzer;
    private boolean isCameraVisible = true;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private CameraSelector cameraSelector;
    private Preview preview;
    private Size optimalPreviewSize;
    private int displayRotation;
    private BarcodeFrame barcodeFrame;


    public CameraViewX(Activity activity, FlutterMethodListener flutterMethodListener) {

        this.activity = activity;
        this.flutterMethodListener = flutterMethodListener;
    }

    @Override
    public void initCamera(RelativeLayout relativeLayout, boolean hasTextReader
            , char flashMode, boolean isFillScale, boolean isTakePictureMode, boolean isScanningText) {
        this.previewFlashMode = flashMode;
        this.isTakePictureMode = isTakePictureMode;
        this.isScanningText = isScanningText;
        userCameraSelector = 0;
        textRecognizer = TextRecognition.getClient();
        displaySize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
        if (isFillScale == true) //fill
            relativeLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    displaySize.x,
                    displaySize.y));

//        textureView = new AutoFitTextureView(activity);
//        textureView.setLayoutParams(new FrameLayout.LayoutParams(
//                ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.MATCH_PARENT));


        previewView = new PreviewView(activity);
        previewView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));


        barcodeFrame = new BarcodeFrame(activity);
        barcodeFrame.setFrameColor(Color.GREEN);
        barcodeFrame.setLaserColor(Color.RED);

        relativeLayout.addView(previewView);

        relativeLayout.addView(barcodeFrame);

        startCamera();

    }


    private int getFlashMode() {
        switch (previewFlashMode) {
            case 'O':
                return ImageCapture.FLASH_MODE_ON;
            case 'F':
                return ImageCapture.FLASH_MODE_OFF;
            case 'A':
                return ImageCapture.FLASH_MODE_AUTO;
            default:
                return ImageCapture.FLASH_MODE_AUTO;
        }
    }


    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
//        int w = aspectRatio.getWidth();
//        int h = aspectRatio.getHeight();

        int w = 16;
        int h = 9;
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CameraView2.CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CameraView2.CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }


    private void prepareOptimalSize() {
        int width = previewView.getWidth();
        int height = previewView.getHeight();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    if (userCameraSelector == 0)
                        continue;
                } else {
                    if (userCameraSelector == 1)
                        continue;
                }
                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }


                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (sensorOrientation == 90 || sensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (sensorOrientation == 0 || sensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }


                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                Size previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight);
                int orientation = activity.getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    optimalPreviewSize = new Size(previewSize.getWidth(), previewSize.getHeight());
                } else {
                    optimalPreviewSize = new Size(previewSize.getHeight(), previewSize.getWidth());
                }

                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            e.printStackTrace();
        }
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(activity);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = cameraProviderFuture.get();

                    prepareOptimalSize();
                    preview = new Preview.Builder()
                            .setTargetResolution(new Size(optimalPreviewSize.getWidth(), optimalPreviewSize.getHeight()))
                            .build();
                    preview.setSurfaceProvider(previewView.createSurfaceProvider());


                    imageCapture = new ImageCapture.Builder()
                            .setFlashMode(getFlashMode())
                            .setTargetResolution(new Size(optimalPreviewSize.getWidth(), optimalPreviewSize.getHeight()))
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build();

                    imageAnalyzer = new ImageAnalysis.Builder()
                            .build();
//                    imageAnalyzer.setAnalyzer(new Executor() {
//                        @Override
//                        public void execute(Runnable command) {
//                            command.run();
//                        }
//                    }, new BarcodeAnalyzer());


                    if (userCameraSelector == 0)
                        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                    else cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                    bindCamera();


                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(activity));
    }


    void setFlashBarcodeReader() {
        if (camera != null) {
            if (previewFlashMode == 'O')
                camera.getCameraControl().enableTorch(true);
            else camera.getCameraControl().enableTorch(false);
        }
    }

    private void bindCamera() {
        cameraProvider.unbind();
        camera = cameraProvider.bindToLifecycle((LifecycleOwner) activity, cameraSelector
                , preview, imageCapture, imageAnalyzer);
        setFlashBarcodeReader();
    

    }


    @Override
    public void setCameraVisible(boolean isCameraVisible) {
        if (isCameraVisible != this.isCameraVisible) {
            this.isCameraVisible = isCameraVisible;
            if (isCameraVisible) resumeCamera2();
            else pauseCamera2();
        }
    }

    @Override
    public void changeFlashMode(char newPreviewFlashMode) {
        previewFlashMode = newPreviewFlashMode;
        imageCapture.setFlashMode(getFlashMode());
        setFlashBarcodeReader();

    }

    Bitmap cropImage(Bitmap bitmap, int rotationDegree, int xOffset, int yOffset, int cropWidth, int cropHeight) {
        // 2 - Rotate the Bitmap
        if (rotationDegree != 0) {
            Matrix rotationMatrix = new Matrix();
            rotationMatrix.postRotate(rotationDegree);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), rotationMatrix, true);
        }

        // 3 - Crop the Bitmap
        bitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, cropWidth, cropHeight);

        return bitmap;
    }

    private int convertExifOreintationToRotation(int orientation) {
        int rotation = 0;

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotation = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotation = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotation = 270;
                break;
        }
        return rotation;
    }

    @Override
    public void takePicture(final MethodChannel.Result result) {
        final File file = getPictureFile();
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(file).build();


        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
                outputOptions, ContextCompat.getMainExecutor(activity), new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        try {

                            ExifInterface exif = null;
                            try {
                                exif = new ExifInterface(file + "");
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_UNDEFINED);

                            Bitmap bitmap = BitmapFactory.decodeFile(file.getPath());

                            if (isTakePictureMode) {
//                            bitmap = cropImage(bitmap, convertExifOreintationToRotation(orientation), barcodeFrame.frameRect.left, barcodeFrame.frameRect.top
//                                    , barcodeFrame.frameRect.right - barcodeFrame.frameRect.left
//                                    , barcodeFrame.frameRect.bottom - barcodeFrame.frameRect.top);
                                textRecognizer.process(InputImage.fromBitmap(bitmap, convertExifOreintationToRotation(orientation)))
                                        .addOnSuccessListener(new OnSuccessListener<Text>() {
                                            @Override
                                            public void onSuccess(Text text) {
                                                processText(text, file + "");
                                            }
                                        })
                                        .addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                flutterMethodListener.onTextRead("Error in reading" + e.getMessage(), null, null);
                                            }
                                        });
                            }

//                            flutterMethodListener.onTakePicture(result, file + "");

                        } catch (Exception e) {
                            e.printStackTrace();
                            flutterMethodListener.onTextRead("Error in reading" + e.getMessage(), null, null);
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
//                        flutterMethodListener.onTakePictureFailed(result, "-1", exception.getMessage());
                    }
                });
    }

    @Override
    public void pauseCamera() {

    }

    @Override
    public void resumeCamera() {
        if (isCameraVisible) {
            setFlashBarcodeReader();
        }
    }

    @Override
    public void dispose() {

    }

    @Override
    public void setScanForText(boolean isScanningText) {
        this.isScanningText = isScanningText;
    }

    @Override
    public void processImageFromPath(String path) {
        try {
            InputImage inputImage = InputImage.fromFilePath(activity
                    , Uri.fromFile(new File(path)));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void pauseCamera2() {
        cameraProvider.unbindAll();
        if (textRecognizer != null) {
            textRecognizer.close();
            textRecognizer = null;
        }
    }

    public void resumeCamera2() {
        if (isCameraVisible) {
            if (textRecognizer == null)
                textRecognizer = TextRecognition.getClient();
            startCamera();
        }
    }

    private File getPictureFile() {
        return new File(activity.getCacheDir(), "pic.jpg");
    }
    
    

    private void processText(Text text, String path) {
        List<LineModel> lineModels = new ArrayList<>();
        for (Text.TextBlock b : text.getTextBlocks())
            for (Text.Line line : b.getLines()) {
                LineModel lineModel = new LineModel(line.getText());
                for (Point p : line.getCornerPoints()) {
                    lineModel.cornerPoints.add(new CornerPointModel(p.x, p.y));
                }
                lineModels.add(lineModel);
            }
        Gson gson = new Gson();
        gson.toJson(lineModels);
        flutterMethodListener.onTextRead(text.getText(), new Gson().toJson(lineModels), path);
    }

    private class BarcodeAnalyzer implements ImageAnalysis.Analyzer {


        @Override
        public void analyze(@NonNull final ImageProxy imageProxy) {
            if (!isScanningText) return;
            @SuppressLint("UnsafeExperimentalUsageError") Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage image =
                        InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

//                textRecognizer.process(image)
//                        .addOnSuccessListener(new OnSuccessListener<Text>() {
//                            @Override
//                            public void onSuccess(Text text) {
//                                processText(text, null);
//                            }
//                        })
//                        .addOnFailureListener(new OnFailureListener() {
//                            @Override
//                            public void onFailure(@NonNull Exception e) {
//                                flutterMethodListener.onTextRead("Error in reading" + e.getMessage(), null, null);
//                            }
//                        })
//                        .addOnCompleteListener(new OnCompleteListener<Text>() {
//                            @Override
//                            public void onComplete(@NonNull Task<Text> task) {
//                                imageProxy.close();
//                            }
//                        });
            }
        }
    }


}
