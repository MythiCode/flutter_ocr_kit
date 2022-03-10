import 'package:flutter/services.dart';
import 'package:ocrkit/OCRKitView.dart';

///This controller is used to control CameraKiView.dart
class OCRKitController {
  MethodChannel? _channel;
  late OCRKitView cameraKitView;

  ///pause camera while stop camera preview.
  ///Plugin manage automatically pause camera based android, iOS lifecycle and widget visibility
  pauseCamera() {
    cameraKitView.viewState.controller!.setCameraVisible(false);
  }

  ///Closing camera and dispose all resource
  closeCamera() {
    cameraKitView.viewState.controller!.closeCamera();
  }

  ///resume camera while resume camera preview.
  ///Plugin manage automatically resume camera based android, iOS lifecycle and widget visibility
  resumeCamera() {
    cameraKitView.viewState.controller!.setCameraVisible(true);
  }

  ///Use this method for taking picture in take picture mode
  ///This method return path of image
  Future<String> takePicture({String path = ""}) {
    return cameraKitView.viewState.controller!.takePicture(path);
  }

  ///Use this method for process image directly with path
  Future<String> processImageFromPath(String path) {
    print("PATH $path");
    return cameraKitView.viewState.controller!.processImageFromPath(path);
  }

  ///Use this method for process image directly with path without need ViewState
  Future<dynamic> processImageFromPathWithoutView(String path) async {
    if (_channel == null) {
      _channel = new MethodChannel('plugins/ocrkit');
    }
    return _channel?.invokeMethod('processImageFromPath', {"path": path});
  }

  ///Change flash mode between auto, on and off
  changeFlashMode(CameraFlashMode captureFlashMode) {
    cameraKitView.viewState.controller!.changeFlashMode(captureFlashMode);
  }

  ///Connect view to this controller
  void setView(OCRKitView cameraKitView) {
    this.cameraKitView = cameraKitView;
  }

  ///Enable and disable scanning for text in live mode.
  void setScanForText(bool isScanningForText) {
    cameraKitView.viewState.controller!.setScanForText(isScanningForText);
  }

// initCamera() {
//   cameraKitView.viewState.controller.initCamera();
// }
}
