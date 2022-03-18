//
//  OCRKitFutterView.swift
//  ocrkit
//
//  Created by faranegar on 1/2/21.
//
import Flutter
import Foundation
import AVFoundation
import MLKitTextRecognition
import MLKitVision

@available(iOS 10.0, *)
class OCRKitFlutterView : NSObject, FlutterPlatformView, AVCaptureVideoDataOutputSampleBufferDelegate, AVCapturePhotoCaptureDelegate {
    let channel: FlutterMethodChannel
    let frame: CGRect

    var hasBarcodeReader:Bool!
    var isCameraVisible:Bool! = true
    var initCameraFinished:Bool! = false
    var isFillScale:Bool!
    var imageSavePath:String!
    var flashMode:AVCaptureDevice.FlashMode!
    var cameraPosition: AVCaptureDevice.Position!
    
    var previewView : UIView!
    var videoDataOutput: AVCaptureVideoDataOutput!
    var videoDataOutputQueue: DispatchQueue!
    
    var photoOutput: AVCapturePhotoOutput?
    var previewLayer:AVCaptureVideoPreviewLayer!
    var captureDevice : AVCaptureDevice!
    let session = AVCaptureSession()
    var flutterResultTakePicture:FlutterResult!
    var textRecognizer : TextRecognizer?
    var isTakePictureMode : Bool!
    var isScanningText : Bool!
    var orientation : UIImage.Orientation!
    
    
    
    init(registrar: FlutterPluginRegistrar, viewId: Int64, frame: CGRect) {
         self.channel = FlutterMethodChannel(name: "plugins/ocr_kit_" + String(viewId), binaryMessenger: registrar.messenger())
        self.frame = frame
     }
    
    
    func requestPermission(flutterResult:  @escaping FlutterResult) {
        if AVCaptureDevice.authorizationStatus(for: .video) ==  .authorized {
            //already authorized
            flutterResult(true)
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                AVCaptureDevice.requestAccess(for: .video, completionHandler: { (granted: Bool) in
                    if granted {
                        //access allowed
                        flutterResult(true)
                    } else {
                        //access denied
                        flutterResult(false)
                    }
                })
            }
        }
    }
    
    
    public func setMethodHandler() {
        self.channel.setMethodCallHandler({ (FlutterMethodCall,  FlutterResult) in
                let args = FlutterMethodCall.arguments
                let myArgs = args as? [String: Any]
                if FlutterMethodCall.method == "requestPermission" {
                    self.requestPermission(flutterResult: FlutterResult)
                } else if FlutterMethodCall.method == "initCamera" {
                    self.initCameraFinished = false
                    
                    //
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                        self.initCamera(hasBarcodeReader: true,
                                        flashMode: (myArgs?["flashMode"] ) as! String,
                                        isFillScale: (myArgs?["isFillScale"] ) as! Bool,
                                        isTakePictureMode:  (myArgs?["isTakePictureMode"] ) as! Bool,
                                        isScanningText:  (myArgs?["isScanningText"] ) as! Bool
                            )
                    }
                } else if FlutterMethodCall.method == "resumeCamera" {
                    
                    if  self.initCameraFinished == true {
                        //self.beginSession(isFirst: false)
             
                        
                        self.session.startRunning()
                        self.isCameraVisible = true
                    }
            }
                else if FlutterMethodCall.method == "pauseCamera" {
                     if self.initCameraFinished == true {
                        self.stopCamera()
                        self.isCameraVisible = false
                    }
                }
            else if FlutterMethodCall.method == "changeFlashMode" {
                    self.setFlashMode(flashMode: (myArgs?["flashMode"] ) as! String)
                    self.changeFlashMode()
                } else if FlutterMethodCall.method == "setCameraVisible" {
             
                    let cameraVisibility = (myArgs?["isCameraVisible"] as! Bool)
                    //print("isCameraVisible: " + String(isCameraVisible))
                    if cameraVisibility == true {
                        if self.isCameraVisible == false {
                            self.session.startRunning()
                            self.isCameraVisible = true
                        }
                    } else {
                           if self.isCameraVisible == true {
                                self.stopCamera()
                                self.isCameraVisible = false
                        }
                    }
                  
                }
             else if FlutterMethodCall.method == "takePicture" {
                    self.flutterResultTakePicture = FlutterResult
                    
                self.takePicture(path: (myArgs?["path"] ) as! String)
                        }
            
             else if FlutterMethodCall.method == "setScanForText" {
                    self.setScanForText(isScanningText:(myArgs?["isScanningText"] ) as! Bool)
                }
             else if FlutterMethodCall.method == "processImageFromPath" {
                self.processImageFromPath(path: (myArgs?["path"] ) as! String)
             }
            })
    }
    
    
    func setScanForText(isScanningText:Bool) {
        self.isScanningText = isScanningText
    }
    
    func changeFlashMode() {
       
        if(self.hasBarcodeReader) {
            do{
               if (captureDevice.hasTorch)
                   {
                       try captureDevice.lockForConfiguration()
                    captureDevice.torchMode = (self.flashMode == .auto) ?(.auto):(self.flashMode == .on ? (.on) : (.off))
                       captureDevice.flashMode = self.flashMode
                       captureDevice.unlockForConfiguration()
                   }
                }catch{
                   //DISABEL FLASH BUTTON HERE IF ERROR
                   print("Device tourch Flash Error ");
               }
          
        }
    }
    
    func setFlashMode(flashMode: String) {
    
        if flashMode == "A" {
            self.flashMode = .auto
                  } else if flashMode == "O" {
            self.flashMode = .on
                  } else if flashMode == "F"{
            self.flashMode = .off
                  }
    }
    
    func view() -> UIView {
        if previewView == nil {
        self.previewView = UIView(frame: frame)
//            previewView.contentMode = UIView.ContentMode.scaleAspectFill
        }
        return previewView
    }
    
    func initCamera(hasBarcodeReader: Bool, flashMode: String, isFillScale: Bool, isTakePictureMode: Bool ,isScanningText: Bool) {
        
        self.isFillScale = isFillScale
        self.isTakePictureMode = isTakePictureMode
        self.isScanningText = isScanningText
        textRecognizer = TextRecognizer.textRecognizer()
        self.cameraPosition = .back

        if(isTakePictureMode == true) {
            self.hasBarcodeReader = false
        } else {
            self.hasBarcodeReader = hasBarcodeReader
        }
        setFlashMode(flashMode: flashMode)
        

        self.setupAVCapture()
    }
    
    @available(iOS 10.0, *)
    func setupAVCapture(){
        session.sessionPreset = AVCaptureSession.Preset.hd1920x1080
          guard let device = AVCaptureDevice
          .default(AVCaptureDevice.DeviceType.builtInWideAngleCamera,
                   for: .video,
                   position: cameraPosition) else {
                              return
          }
          captureDevice = device
    
       
          beginSession()
          changeFlashMode()
      }
    
    
    func beginSession(isFirst: Bool = true){
        var deviceInput: AVCaptureDeviceInput!
        do {
            deviceInput = try AVCaptureDeviceInput(device: captureDevice)
            guard deviceInput != nil else {
                print("error: cant get deviceInput")
                return
            }
            
            if self.session.canAddInput(deviceInput){
                self.session.addInput(deviceInput)
            }
            
            orientation = imageOrientation(
               fromDevicePosition: cameraPosition
             )

            if(hasBarcodeReader) {
                videoDataOutput = AVCaptureVideoDataOutput()
                videoDataOutput.alwaysDiscardsLateVideoFrames = true
               
                videoDataOutputQueue = DispatchQueue(label: "VideoDataOutputQueue")
                videoDataOutput.setSampleBufferDelegate(self, queue:self.videoDataOutputQueue)
                if session.canAddOutput(videoDataOutput!){
                             session.addOutput(videoDataOutput)
                 }
                videoDataOutput.connection(with: .video)?.isEnabled = true

            }
            else {
                photoOutput = AVCapturePhotoOutput()
                    photoOutput?.setPreparedPhotoSettingsArray([AVCapturePhotoSettings(format: [AVVideoCodecKey : AVVideoCodecJPEG])], completionHandler: nil)
                if session.canAddOutput(photoOutput!){
                    session.addOutput(photoOutput!)
                }
            }

            previewLayer = AVCaptureVideoPreviewLayer(session: self.session)
            if self.isFillScale == true {
                previewLayer.videoGravity = AVLayerVideoGravity.resizeAspectFill
            } else {
                previewLayer.videoGravity = AVLayerVideoGravity.resizeAspect
            }

          
            startSession(isFirst: isFirst)
            
         
            
        } catch let error as NSError {
            deviceInput = nil
            print("error: \(error.localizedDescription)")
        }
    }
    
    
    func startSession(isFirst: Bool) {
        DispatchQueue.main.async {
        let rootLayer :CALayer = self.previewView.layer
        rootLayer.masksToBounds = true
        if(rootLayer.bounds.size.width != 0 && rootLayer.bounds.size.width != 0){
            self.previewLayer.frame = rootLayer.bounds
            rootLayer.addSublayer(self.previewLayer)
            self.session.startRunning()
            if isFirst == true {
            DispatchQueue.global().asyncAfter(deadline: .now() + 0.2) {
                    self.initCameraFinished = true
                           }
            }
        } else {
            DispatchQueue.global().asyncAfter(deadline: .now() + 1.0) {
                self.startSession(isFirst: isFirst)
                           }
            }
        }
    }
    
    
    func stopCamera(){
        if session.isRunning {
            session.stopRunning()
        }
    }

    
     private func currentUIOrientation() -> UIDeviceOrientation {
        let deviceOrientation = { () -> UIDeviceOrientation in
          switch UIApplication.shared.statusBarOrientation {
          case .landscapeLeft:
            return .landscapeRight
          case .landscapeRight:
            return .landscapeLeft
          case .portraitUpsideDown:
            return .portraitUpsideDown
          case .portrait, .unknown:
            return .portrait
          @unknown default:
            fatalError()
          }
        }
        guard Thread.isMainThread else {
          var currentOrientation: UIDeviceOrientation = .portrait
          DispatchQueue.main.sync {
            currentOrientation = deviceOrientation()
          }
          return currentOrientation
        }
        return deviceOrientation()
      }
    
    
    public func imageOrientation(
      fromDevicePosition devicePosition: AVCaptureDevice.Position = .back
    ) -> UIImage.Orientation {
      var deviceOrientation = UIDevice.current.orientation
      if deviceOrientation == .faceDown || deviceOrientation == .faceUp
        || deviceOrientation
          == .unknown
      {
        deviceOrientation = currentUIOrientation()
      }
      switch deviceOrientation {
      case .portrait:
        return devicePosition == .front ? .leftMirrored : .right
      case .landscapeLeft:
        return devicePosition == .front ? .downMirrored : .up
      case .portraitUpsideDown:
        return devicePosition == .front ? .rightMirrored : .left
      case .landscapeRight:
        return devicePosition == .front ? .upMirrored : .down
      case .faceDown, .faceUp, .unknown:
        return .up
      @unknown default:
        fatalError()
      }
    }
    func saveImage(image: UIImage) ->
    String? {
        guard let data = image.jpegData(compressionQuality: 1) ?? image.pngData() else {
            return nil
        }
        var fileURL : URL? = nil
        if self.imageSavePath == "" {
            guard let directory = try? FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: false) as NSURL else {
                return nil
            }
            fileURL = directory.appendingPathComponent("pic.jpg")!
        } else  {
            fileURL = URL(fileURLWithPath: self.imageSavePath)
        }
     
        do {
          
            try data.write(to: fileURL!)
            return fileURL!.path
            //print(directory)
            
        } catch {
            print(error.localizedDescription)
                        flutterResultTakePicture(FlutterError(code: "-103", message: error.localizedDescription, details: nil))
            return nil
        }
    }
    
    private func processImageFromPath(path: String) {
        let fileURL = URL(fileURLWithPath: path)
        do {
            let imageData = try Data(contentsOf: fileURL)
            let image = UIImage(data: imageData)
            if image == nil {
                return
            }
            let visionImage = VisionImage(image: image!)
            visionImage.orientation = image!.imageOrientation
            proccessImage(visionImage: visionImage, selectedImagePath: path)
        } catch {
            print("Error loading image : \(error)")
        }

    }
    
    func takePicture(path : String) {
        self.imageSavePath = path
        let settings = AVCapturePhotoSettings()
        if captureDevice.hasFlash {
            settings.flashMode = self.flashMode
        }
        photoOutput?.capturePhoto(with: settings, delegate:self)
    }
    
    public func photoOutput(_ captureOutput: AVCapturePhotoOutput, didFinishProcessingPhoto photoSampleBuffer: CMSampleBuffer?, previewPhoto previewPhotoSampleBuffer: CMSampleBuffer?,
                        resolvedSettings: AVCaptureResolvedPhotoSettings, bracketSettings: AVCaptureBracketedStillImageSettings?, error: Swift.Error?) {
        if let error = error { //self.photoCaptureCompletionBlock?(nil, error)
            flutterResultTakePicture(FlutterError(code: "-101", message: error.localizedDescription, details: nil))
        } else if let buffer = photoSampleBuffer, let data = AVCapturePhotoOutput.jpegPhotoDataRepresentation(forJPEGSampleBuffer: buffer, previewPhotoSampleBuffer: nil),
                     let image = UIImage(data: data) {
            if(isTakePictureMode == true) {
                let visionImage = VisionImage(image: image)
                visionImage.orientation = orientation
                proccessImage(visionImage: visionImage, image: image)
            } else {
                flutterResultTakePicture(self.saveImage(image: image))
         
             }
        }
            
        else {
            //error
//            self.photoCaptureCompletionBlock?(nil, CameraControllerError.unknown)
                        flutterResultTakePicture(FlutterError(code: "-102", message: "Unknown error", details: nil))
        }
    }
    
    func proccessImage(visionImage: VisionImage, image: UIImage? = nil, selectedImagePath : String? = nil) {
        if textRecognizer != nil {
                
                 
                var path : String?
                if image != nil {
                    path = self.saveImage(image: image!)
                }
                else {
                    path = selectedImagePath
                }
            
                 textRecognizer?.process(visionImage) { result, error in
                   guard error == nil, let result = result else {
                     // Error handling
                    self.textRead(barcode: "Error: " + error.debugDescription, values: "", path: "", orientation: nil)
                     return
                   }
                   // Recognized text
                   
                    
                    if(result.text != "") {
                        var listLineModel: [LineModel] = []
                        var values : String = ""
                        
                        for b in result.blocks {
                            for l in b.lines{
                                var lineModel = LineModel(text: "", cp: [])
                                lineModel.text = l.text
                                
                            
                                
                                for c in l.cornerPoints {
                                    lineModel.cornerPoints
                                        .append(
                                            CornerPointModel(x: c.cgPointValue.x, y: c.cgPointValue.y))
                                }
                           
                
                                
                                listLineModel.append(lineModel)
                                
                            }
                        }
                        
                        
                        do{
                            let jsonData =  try JSONEncoder().encode(listLineModel)
                            values = String(data: jsonData, encoding: String.Encoding.utf8)!
                            }catch{
                                 fatalError("Unable To Convert in Json")
                            }
                      
                    
                       
                        self.textRead(barcode: result.text, values: values, path: path, orientation:  visionImage.orientation.rawValue)

                    } else {
                        
                         self.textRead(barcode: "", values: nil, path: path, orientation:  nil)
                    }
                 }
            
            
             }
    }
    

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        
        if(self.isScanningText) {
            let visionImage = VisionImage(buffer: sampleBuffer)
            visionImage.orientation = orientation
            var listLineModel: [LineModel] = []
            var values = ""

            do {
                var results = try textRecognizer?.results(in: visionImage)
                
                for block in results!.blocks {
                    for line in block.lines {
                        var cp = [CornerPointModel]()
                        for c in line.cornerPoints {
                            cp.append(CornerPointModel(x: c.cgPointValue.x, y: c.cgPointValue.y))
                        }

                        listLineModel.append(LineModel(text: line.text, cp: cp))

                    }
                }
                
                do{
                    let jsonData =  try JSONEncoder().encode(listLineModel)
                    values = String(data: jsonData, encoding: String.Encoding.utf8)!
                    }catch{
                         fatalError("Unable To Convert in Json")
                    }
                
                self.textRead(barcode: results!.text, values: values, path: nil, orientation:  visionImage.orientation.rawValue)

            } catch {
                print("can't fetch result")
            }
        }
    }
    
    func textRead(barcode: String, values: String?, path: String?, orientation: Int?) {
        let arguments: [String: Any] = [
                 "barcode": barcode,
                 "values": values,
                 "path": path,
                 "orientation": orientation
                   ]
        channel.invokeMethod("onTextRead", arguments: arguments)
    }
    
    
}

class LineModel: Codable {
    var text:String = ""
    var cornerPoints : [CornerPointModel] = []
    
    init(text: String, cp: [CornerPointModel]) {
        self.text = text
        self.cornerPoints = cp
    }
}


class CornerPointModel: Codable {
    
    init(x:CGFloat, y:CGFloat) {
        self.x = x
        self.y = y
    }
    
    var x:CGFloat
    var y:CGFloat
}

