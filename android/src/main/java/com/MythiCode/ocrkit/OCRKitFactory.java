package com.MythiCode.ocrkit;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.StandardMessageCodec;
import io.flutter.plugin.platform.PlatformView;
import io.flutter.plugin.platform.PlatformViewFactory;

public class OCRKitFactory extends PlatformViewFactory {
    private final ActivityPluginBinding pluginBinding;
    private final BinaryMessenger binaryMessenger;

    public OCRKitFactory(ActivityPluginBinding pluginBinding, BinaryMessenger binaryMessenger) {
        super(StandardMessageCodec.INSTANCE);

        this.pluginBinding = pluginBinding;
        this.binaryMessenger = binaryMessenger;
        new OCRKitFlutterView(pluginBinding, binaryMessenger, 0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public PlatformView create(Context context, int viewId, Object args) {
        return new OCRKitFlutterView(pluginBinding, binaryMessenger, viewId);
    }
}
