import 'dart:async';

import 'package:flutter/services.dart';

class Ocrkit {
  static const MethodChannel _channel = const MethodChannel('ocrkit');

  static Future<String?> get platformVersion async {
    final String? version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<String?> get processImageFromPathWithoutView async {
    final String? version = await _channel.invokeMethod('processImageFromPathWithoutView');
    return version;
  }


}
