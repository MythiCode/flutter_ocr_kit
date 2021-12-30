import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:ocrkit/OCRKitController.dart';
import 'package:visibility_detector/visibility_detector.dart';

enum CameraFlashMode { on, off, auto }
enum ScaleTypeMode { fit, fill }

class OCRKitView extends StatefulWidget {
  /// In barcodeReader mode, while camera preview detect barcodes, This method is called.
  final Function onTextRead;

  ///After android and iOS user deny run time permission, this method is called.
  final Function onPermissionDenied;

  ///There are 2 modes `ScaleTypeMode.fill` and `ScaleTypeMode.fit` for this parameter.
  ///If you want camera preview fill your widget area, use `fill` mode. In this mode, camera preview may be croped for filling widget area.
  ///If you want camera preview to show entire lens preview, use `fit` mode. In this mode, camera preview may be shows blank areas.
  final ScaleTypeMode scaleType;

  ///This parameter accepts 3 values. `CameraFlashMode.auto`, `CameraFlashMode.on` and `CameraFlashMode.off`.
  /// For changing value after initial use `changeFlashMode` method in controller.
  final CameraFlashMode previewFlashMode;

  ///Controller for this widget
  final OCRKitController ocrKitController;

  ///While taking picture, if `isTakePictureMode` is true, the output is processed
  final bool isTakePictureMode;

  late _OCRScannerViewState viewState;

  ///if false, live mode doesn't process anything.
  final bool isScanningText;

  OCRKitView({
    this.scaleType = ScaleTypeMode.fill,
    required this.onTextRead,
    this.previewFlashMode = CameraFlashMode.auto,
    required this.ocrKitController,
    required this.onPermissionDenied,
    this.isTakePictureMode = false,
    this.isScanningText = true,
  });

  @override
  State<StatefulWidget> createState() {
    if (ocrKitController != null) ocrKitController.setView(this);
    viewState = _OCRScannerViewState();
    return viewState;
  }
}

class _OCRScannerViewState extends State<OCRKitView>
    with WidgetsBindingObserver {
   NativeCameraKitController? controller;
   late VisibilityDetector visibilityDetector;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance!.addObserver(this);
    if (defaultTargetPlatform == TargetPlatform.android) {
      visibilityDetector = VisibilityDetector(
          key: Key('visible-camerakit-key-1'),
          onVisibilityChanged: (visibilityInfo) {
            if (controller != null) {
              if (visibilityInfo.visibleFraction == 0)
                controller!.setCameraVisible(false);
              else
                controller!.setCameraVisible(true);
            }
          },
          child: AndroidView(
            viewType: 'plugins/ocr_kit',
            onPlatformViewCreated: _onPlatformViewCreated,
          ));
    } else {
      visibilityDetector = VisibilityDetector(
          key: Key('visible-camerakit-key-1'),
          onVisibilityChanged: (visibilityInfo) {
            if (visibilityInfo.visibleFraction == 0)
              controller!.setCameraVisible(false);
            else
              controller!.setCameraVisible(true);
          },
          child: UiKitView(
            viewType: 'plugins/ocr_kit',
            onPlatformViewCreated: _onPlatformViewCreated,
          ));
    }
  }

  @override
  Widget build(BuildContext context) {
    return visibilityDetector;
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.resumed:
        print("Flutter Life Cycle: resumed");
        if (controller != null) controller!.resumeCamera();
        break;
      case AppLifecycleState.inactive:
        print("Flutter Life Cycle: inactive");
        if (Platform.isIOS) {
          controller!.pauseCamera();
        }
        break;
      case AppLifecycleState.paused:
        print("Flutter Life Cycle: paused");
        controller!.pauseCamera();
        break;
      default:
        break;
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance!.removeObserver(this);
    super.dispose();
  }

  void _onPlatformViewCreated(int id) {
    this.controller = new NativeCameraKitController._(id, context, widget);
    this.controller!.initCamera();
  }

  void disposeView() {
    controller!.dispose();
  }
}

class NativeCameraKitController {
  BuildContext context;
  OCRKitView widget;

  NativeCameraKitController._(int id, this.context, this.widget)
      : _channel = new MethodChannel('plugins/ocr_kit_' + id.toString());

  final MethodChannel _channel;

  Future<dynamic> nativeMethodCallHandler(MethodCall methodCall) async {
    if (methodCall.method == "onTextRead") {
      if (widget.onTextRead != null)
        widget.onTextRead(
            methodCall.arguments["barcode"],
            methodCall.arguments["values"],
            methodCall.arguments["path"],
            methodCall.arguments["orientation"]);
    }

    return null;
  }

  bool _getScaleTypeMode(ScaleTypeMode scaleType) {
    if (scaleType == ScaleTypeMode.fill)
      return true;
    else
      return false;
  }

  String? _getCharFlashMode(CameraFlashMode cameraFlashMode) {
    String? flashMode;
    switch (cameraFlashMode) {
      case CameraFlashMode.auto:
        flashMode = "A";
        break;
      case CameraFlashMode.on:
        flashMode = "O";
        break;
      case CameraFlashMode.off:
        flashMode = "F";
        break;
    }
    return flashMode;
  }

  void initCamera() async {
    _channel.setMethodCallHandler(nativeMethodCallHandler);
    _channel.invokeMethod('requestPermission').then((value) {
      if (value) {
        if (Platform.isAndroid) {
          _channel.invokeMethod('initCamera', {
            "flashMode": _getCharFlashMode(widget.previewFlashMode),
            "isFillScale": _getScaleTypeMode(widget.scaleType),
            "isTakePictureMode": widget.isTakePictureMode,
            "isScanningText": widget.isScanningText
          });
        } else {
          _channel.invokeMethod('initCamera', {
            "flashMode": _getCharFlashMode(widget.previewFlashMode),
            "isFillScale": _getScaleTypeMode(widget.scaleType),
            "isTakePictureMode": widget.isTakePictureMode,
            "isScanningText": widget.isScanningText
          });
        }
      } else {
        widget.onPermissionDenied();
      }
    });
  }

  ///Enable and disable scanning for text in live mode.
  Future<void> setScanForText(bool isScanningText) async {
    return _channel
        .invokeMethod('setScanForText', {"isScanningText": isScanningText});
  }

  ///Call resume camera in Native API
  Future<void> resumeCamera() async {
    return _channel.invokeMethod('resumeCamera');
  }

  ///Call pause camera in Native API
  Future<void> pauseCamera() async {
    return _channel.invokeMethod('pauseCamera');
  }

  ///Call close camera in Native API
  Future<void> closeCamera() {
    return _channel.invokeMethod('closeCamera');
  }

  ///Call take picture in Native API
  Future<String> takePicture(String path) async {
    return "${_channel.invokeMethod('takePicture', {"path": path})}";
  }

  ///Call change flash mode in Native API
  Future<void> changeFlashMode(CameraFlashMode captureFlashMode) {
    return _channel.invokeMethod(
        'changeFlashMode', {"flashMode": _getCharFlashMode(captureFlashMode)});
  }

  ///Call dispose in Native API
  Future<void> dispose() {
    return _channel.invokeMethod('dispose', "");
  }

  ///Call set camera visible in Native API.
  ///This API is used to automatically manage pause and resume camera
  Future<void> setCameraVisible(bool isCameraVisible) {
    return _channel
        .invokeMethod('setCameraVisible', {"isCameraVisible": isCameraVisible});
  }

  Future<String> processImageFromPath(String path) async {
    return "${_channel.invokeMethod('processImageFromPath', {"path": path})}";
  }
}
