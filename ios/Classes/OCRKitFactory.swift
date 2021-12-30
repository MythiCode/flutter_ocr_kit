//
//  OCRKitFactory2.swift
//  GoogleDataTransport
//
//  Created by faranegar on 1/2/21.
//

import Foundation
import Flutter

public class OCRKitFactory: NSObject, FlutterPlatformViewFactory {

let registrar:FlutterPluginRegistrar

    public func create(withFrame frame: CGRect, viewIdentifier viewId: Int64, arguments args: Any?) -> FlutterPlatformView {
       let ocrKitFlutterView = OCRKitFlutterView(registrar: self.registrar, viewId: viewId, frame: frame)
       ocrKitFlutterView.setMethodHandler()
       return ocrKitFlutterView
   }




   init(registrar: FlutterPluginRegistrar) {
       self.registrar = registrar
   }
}
