import 'dart:convert';
import 'package:camerakit/CameraKitController.dart';
import 'package:camerakit/CameraKitView.dart';
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
  CameraKitController cc = CameraKitController();

  takePicture() async {
    // String? path = await oc.takePicture();
    String path = (await cc.takePicture())!;
    final result = await OCRKitController().processImageFromPathWithoutView(path);
    Map<String, dynamic> data = jsonDecode(result);
    print(data);
    // oc.processImageFromPath(
    //     "/var/mobile/Containers/Data/Application/4A11BF1B-5CF1-4986-930B-61B270CB8EAB/Documents/pic.jpg");
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
      floatingActionButton: FloatingActionButton(
        onPressed: () => takePicture(),
        child: const Icon(Icons.camera),
      ),
      body: Center(
        child: CameraKitView(cameraKitController: cc),
      ),
      // body: Center(
      //   child: OCRKitView(
      //       isTakePictureMode: true,
      //       onTextRead: (barcode, values, path, orientation) {
      //         print("Barcode:========================= $barcode");
      //         print("Path:============================ $path");
      //         print("values:=========================== $values");
      //       },
      //       ocrKitController: oc,
      //       onPermissionDenied: () {}),
      // ),
    );
  }
}
