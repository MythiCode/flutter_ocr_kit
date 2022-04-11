import 'dart:convert';

import 'package:camerakit/CameraKitController.dart';
import 'package:camerakit/CameraKitView.dart';
import 'package:flutter/material.dart';
import 'package:ocrkit/OCRKitController.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        // This is the theme of your application.
        //
        // Try running your application with "flutter run". You'll see the
        // application has a blue toolbar. Then, without quitting the app, try
        // changing the primarySwatch below to Colors.green and then invoke
        // "hot reload" (press "r" in the console where you ran "flutter run",
        // or simply save your changes to "hot reload" in a Flutter IDE).
        // Notice that the counter didn't reset back to zero; the application
        // is not restarted.
        primarySwatch: Colors.blue,
      ),
      home: const MyHomePage(title: 'Flutter Demo Home Page'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({Key? key, required this.title}) : super(key: key);

  // This widget is the home page of your application. It is stateful, meaning
  // that it has a State object (defined below) that contains fields that affect
  // how it looks.

  // This class is the configuration for the state. It holds the values (in this
  // case the title) provided by the parent (in this case the App widget) and
  // used by the build method of the State. Fields in a Widget subclass are
  // always marked "final".

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  // OCRKitController oc = OCRKitController();
  CameraKitController? cc;

  @override
  void initState() {
    super.initState();
    cc = CameraKitController();
    print("CameraKitController" + cc.toString());
  }

  takePicture() async {
    // oc.takePicture();
    try {
      String? path = await cc!.takePicture();
      final result = await OCRKitController().processImageFromPathWithoutView(path!);
      Map<String, dynamic> data = jsonDecode(result);
      print(data);
    } catch (e) {
      print("Exeption $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
      floatingActionButton: ElevatedButton(onPressed: () => takePicture(), child: const Icon(Icons.camera)),
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
