import Flutter
import UIKit
import MLKitTextRecognition
import MLKitVision

public class SwiftOcrkitPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "plugins/ocrkit", binaryMessenger: registrar.messenger())
    let instance = SwiftOcrkitPlugin()
    
    registrar.register(OCRKitFactory(registrar: registrar), withId: "plugins/ocr_kit")
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    result("iOS " + UIDevice.current.systemVersion)
  }
    
    private func processImageFromPath(path: String, result: @escaping FlutterResult) {
        let fileURL = URL(fileURLWithPath: path)
        do {
            let imageData = try Data(contentsOf: fileURL)
            let image = UIImage(data: imageData)
            if image == nil {
                return
            }
            let visionImage = VisionImage(image: image!)
            visionImage.orientation = image!.imageOrientation
            proccessImage(visionImage: visionImage, selectedImagePath: path, resultFlutter: result)
        } catch {
            print("Error loading image : \(error)")
        }

    }
    
    func proccessImage(visionImage: VisionImage, image: UIImage? = nil, selectedImagePath : String? = nil, resultFlutter: @escaping FlutterResult) {
        var textRecognizer : TextRecognizer?
        textRecognizer = TextRecognizer.textRecognizer()

        if textRecognizer != nil {
                
                 
                var path = selectedImagePath
               
            
                 textRecognizer?.process(visionImage) { result, error in
                   guard error == nil, let result = result else {
                     // Error handling
                    resultFlutter(FlutterError.init(code: "NATIVE_ERR",
                                                    message: "Error1" ,
                                                    details: nil))
                     return
                   }
                   // Recognized text
                   
                    
                    if(result.text != "") {
                        var listLineModel: [LineModel] = []
                        var values : String = ""
                        
                        for b in result.blocks {
                            for l in b.lines{
                                var lineModel : LineModel = LineModel()
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
                      
                        let arguments: [String: Any] = [
                            "barcode": result.text,
                                 "values": values,
                                 "path": path
                                   ]
                        resultFlutter(arguments)

                    } else {
                        
                        resultFlutter(FlutterError.init(code: "NATIVE_ERR",
                                                        message: "Error1" ,
                                                        details: nil))
                    }
                    
              
                    
                 }
            
            
             }
    }
    
    
    
}


