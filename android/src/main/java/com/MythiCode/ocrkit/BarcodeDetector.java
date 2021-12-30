package com.MythiCode.ocrkit;

import android.media.Image;
import android.media.ImageReader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.gson.Gson;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;

public class BarcodeDetector {

    private static ImageReader imageReader;
    private static boolean isBusy = false;

    public static void setImageReader(ImageReader imageReader) {
        BarcodeDetector.imageReader = imageReader;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static void detectImage(final ImageReader imageReader, TextRecognizer textRecognizer
            , final Image inputImage, final FlutterMethodListener flutterMethodListener, int firebaseOrientation) {


        if(!isBusy) {
            isBusy = true;
//            textRecognizer
//                    .process(InputImage.fromMediaImage(inputImage, firebaseOrientation))
//                    .addOnSuccessListener(new OnSuccessListener<Text>() {
//                        @Override
//                        public void onSuccess(Text text) {
//                            flutterMethodListener.onTextRead(text.getText(), new Gson().toJson(lineModels), text.getText());
//                        }
//                    }).addOnFailureListener(new OnFailureListener() {
//                @Override
//                public void onFailure(@NonNull Exception e) {
//                    flutterMethodListener.onTextRead(text.getText(), new Gson().toJson(lineModels), "Error in reading" + e.getMessage());
//                }
//            })
//            .addOnCompleteListener(new OnCompleteListener<Text>() {
//                @Override
//                public void onComplete(@NonNull Task<Text> task) {
//                    isBusy = false;
//                    inputImage.close();
//                }
//            });
        } else {
            inputImage.close();
        }

    }


}
