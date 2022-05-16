package com.MythiCode.ocrkit;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.ExifInterface;
import android.os.Build;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.MythiCode.ocrkit.Model.CornerPointModel;
import com.MythiCode.ocrkit.Model.LineModel;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.gson.Gson;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.platform.PlatformView;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class OCRKitFlutterView implements PlatformView, MethodChannel.MethodCallHandler, FlutterMethodListener {
    private static final int REQUEST_CAMERA_PERMISSION = 10001;
    private final MethodChannel channel;
    private final ActivityPluginBinding activityPluginBinding;
    private CameraBaseView cameraView;
    FlutterMethodListener flutterMethodListener;
    MethodChannel.Result resultTest;

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull final MethodChannel.Result result) {
        Log.e("OCRKitFlutterView", "onMethodCall:" + call.method);
        resultTest = result;
        switch (call.method) {
            case "requestPermission":
                if (ActivityCompat.checkSelfPermission(activityPluginBinding.getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activityPluginBinding.getActivity(), new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                    activityPluginBinding.addRequestPermissionsResultListener(new PluginRegistry.RequestPermissionsResultListener() {
                        @Override
                        public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
                            for (int i :
                                    grantResults) {
                                if (i == PackageManager.PERMISSION_DENIED) {
                                    try {
                                        result.success(false);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    return false;
                                }
                            }
                            try {
                                result.success(true);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return false;
                        }
                    });
                    return;
                } else {
                    try {
                        result.success(true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            case "initCamera":
//            boolean hasBarcodeReader = call.argument("hasBarcodeReader");
                char flashMode = call.argument("flashMode").toString().charAt(0);
                boolean isFillScale = call.argument("isFillScale");
                boolean isTakePictureMode = call.argument("isTakePictureMode");
                boolean isScanningText = call.argument("isScanningText");
                getCameraView().initCamera(true, flashMode, isFillScale, isTakePictureMode, isScanningText);
                break;
            case "resumeCamera":
                getCameraView().resumeCamera();

                break;
            case "pauseCamera":
                getCameraView().pauseCamera();
                break;
            case "takePicture":
                getCameraView().takePicture(result);
                break;
            case "changeFlashMode":
                char captureFlashMode = call.argument("flashMode").toString().charAt(0);
                getCameraView().changeFlashMode(captureFlashMode);
                break;
            case "dispose":
                dispose();
                break;
            case "setCameraVisible": {
                boolean isCameraVisible = call.argument("isCameraVisible");
                getCameraView().setCameraVisible(isCameraVisible);
                break;
            }

            case "setScanForText": {
                boolean isCameraVisible = call.argument("isScanningText");
                getCameraView().setScanForText(isCameraVisible);
                break;
            }
            case "processImageFromPath":
                String path = call.argument("path");
                getCameraView().processImageFromPath(path);
                break;

            case "processImageFromPathWithoutView":
                String path1 = call.argument("path");
                try {
                    //  result.success(processImageFromPathWithoutView(path1));
                    processImageFromPathWithoutView(path1, result);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private CameraBaseView getCameraView() {
        return cameraView;
    }

    public OCRKitFlutterView(ActivityPluginBinding activityPluginBinding, BinaryMessenger binaryMessenger, int viewId) {
        this.channel = new MethodChannel(binaryMessenger, "plugins/ocrkit");
        Log.e("CHANNEL", "plugins/ocrkit" + viewId);
        this.activityPluginBinding = activityPluginBinding;
        this.channel.setMethodCallHandler(this);
        if (getCameraView() == null) {
            cameraView = new CameraBaseView(activityPluginBinding.getActivity(), this);
        }
    }

    @Override
    public View getView() {
        return getCameraView().getView();
    }

    @Override
    public void dispose() {
        if (getCameraView() != null) {
            getCameraView().dispose();
        }
    }

    @Override
    public void onTextRead(String text, String values, String path) {
        Log.d("OCRKitFlutterView", "onTextRead");
        HashMap<String, Object> arguments = new HashMap<>();
        arguments.put("barcode", text);
        arguments.put("values", values);
        arguments.put("path", path);
        channel.invokeMethod("onTextRead", arguments);
    }

    @Override
    public void onTakePicture(final MethodChannel.Result result, final String filePath) {
        activityPluginBinding.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                result.success(filePath);
            }
        });
    }

    private void processText(Text text, String path) {
        Log.d("OCRKitFlutterView", "processText => " + text.getText());
        ArrayList<LineModel> lineModels = new ArrayList<>();
        for (int i = 0; i < text.getTextBlocks().size(); i++) {
            for (int j = 0; j < text.getTextBlocks().get(i).getLines().size(); j++) {
                Text.Line line = text.getTextBlocks().get(i).getLines().get(j);
                LineModel lineModel = new LineModel(line.getText());
                for (int k = 0; k < Objects.requireNonNull(line.getCornerPoints()).length; k++) {
                    Point point = line.getCornerPoints()[k];
                    lineModel.cornerPoints.add(new CornerPointModel(point.x, point.y));
                }
                lineModels.add(lineModel);
            }
        }
        Gson gson = new Gson();
        gson.toJson(lineModels);
        onTextRead(text.getText(), new Gson().toJson(lineModels), path);
        Log.d("OCRKitFlutterView", "lineModels" + lineModels);
    }

    @Override
    public void onTakePictureFailed(final MethodChannel.Result result, final String errorCode, final String errorMessage) {
        activityPluginBinding.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                result.error(errorCode, errorMessage, null);
            }
        });
    }

    private void processImageFromPathWithoutView(final String path, final MethodChannel.Result result) throws IOException {
        Log.d("OCRKitFlutterView", "processImageFromPathWithoutView: " + path);
        ExifInterface exif = new ExifInterface(path);
        int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        int rotationInDegrees = exifToDegrees(rotation);

        Bitmap bitmap = BitmapFactory.decodeFile(path);
        final InputImage inputImage = InputImage.fromBitmap(bitmap, rotationInDegrees);

        TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        final Map<String, String> map = new HashMap<>();
        recognizer.process(inputImage).addOnSuccessListener(new OnSuccessListener<Text>() {
            @Override
            public void onSuccess(Text text) {
                ArrayList<Map<String, String>> listPoints = new ArrayList<>();
                map.put("text", text.getText());
                map.put("path", path);
                map.put("orientation", "0");

                for (int i = 0; i < text.getTextBlocks().size(); i++) {
                    Text.TextBlock block = text.getTextBlocks().get(i);
                    processText(text, path);
                    for (int j = 0; j < block.getLines().size(); j++) {
                        Text.Line line = block.getLines().get(j);
                        for (int k = 0; k < line.getElements().size(); k++) {
                            Map<String, String> value = new HashMap<>();
                            Text.Element element = line.getElements().get(k);

                            Point[] elementCornerPoints = element.getCornerPoints();
                            value.put("text", element.getText());
                            value.put("cornerPoints", Objects.requireNonNull(elementCornerPoints).toString());
                            listPoints.add(value);
                        }
                    }
                }
                map.put("values", listPoints.toString());
                Log.d("OCRKitFlutterView", "map " + map);
                Gson gson = new Gson();
                String json = gson.toJson(map);
                result.success(json);
            }
        }).addOnCanceledListener(new OnCanceledListener() {
            @Override
            public void onCanceled() {

            }
        });

    }

    private static int exifToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }

}
