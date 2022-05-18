import 'dart:convert';
// import 'package:camerakit/CameraKitController.dart';
// import 'package:camerakit/CameraKitView.dart';
import 'package:flutter/material.dart';
import 'package:ocrkit/OCRKitController.dart';
import 'package:ocrkit/OCRKitView.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: const MyHomePage(title: 'Flutter Demo Home Page'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({Key? key, required this.title}) : super(key: key);
  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  OCRKitController oc = OCRKitController();
  // CameraKitController? cc;

  @override
  void initState() {
    super.initState();
    // cc = CameraKitController();
  //  print("CameraKitController" + cc.toString());
  }

  takePicture() async {
    try {
      ///Stopwatch stopwatch = Stopwatch()..start();  for testing the speed of process
      // String path = await cc!.takePicture();
      String? path = await oc.takePicture();
      final result = await oc.processImageFromPathWithoutView(path);

      Map<String, dynamic> data = jsonDecode(result);
      print("data ===================================================> $data");
     // print('result executed in ${stopwatch.elapsed}');
    } catch (e) {
      print("Exeption $e");
    }
  }

  @override
  void dispose() {
    // cc?.closeCamera();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
      floatingActionButton: FloatingActionButton(onPressed: () => takePicture(), child: const Icon(Icons.camera)),
      // body: Center(
      //   child: CameraKitView(cameraKitController: cc),
      // ),
      body: Center(
        child: OCRKitView(
            isTakePictureMode: false,
            onTextRead: (barcode, values, path, orientation) {
              print("Barcode:========================= $barcode");
              print("Path:============================ $path");
              print("values:=========================== $values");
            },
            ocrKitController: oc,
            onPermissionDenied: () {}),
      ),
    );
  }
}
