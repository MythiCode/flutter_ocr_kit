package com.MythiCode.ocrkit

import android.annotation.SuppressLint
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class TextReaderAnalyzer : ImageAnalysis.Analyzer {
    private val tag = "==r"

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        Log.e(tag, "TextReaderAnalyzer: $imageProxy")
        val mediaImage = imageProxy.image
        val recognizer: TextRecognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            // Pass image to an ML Kit Vision API
            val result: Task<Text> = recognizer.process(image)
                .addOnSuccessListener {
                    // Task completed successfully

                    for (block in it.textBlocks) {
                        val blockText = block.text
                        val blockCornerPoints: Array<Point> = block.cornerPoints!!
                        val blockFrame: Rect = block.boundingBox!!
                        Log.d(tag, "BlockText $blockText")

                        for (line in block.lines) {
                            val lineText = line.text
                            val lineCornerPoints: Array<Point> = line.cornerPoints!!
                            val lineFrame: Rect = line.boundingBox!!
                            Log.d(tag, "LineText $lineText")
                            for (element in line.elements) {
                                val elementText = element.text
                                val elementCornerPoints: Array<Point> = element.cornerPoints!!
                                val elementFrame: Rect = element.boundingBox!!
                                Log.d(tag, "ElementText $elementText")
                            }
                        }
                    }
                    imageProxy.close()
                }
                .addOnFailureListener {
                    // Task failed with an exception
                    Log.d(tag, "Failed to load the image")

                }
        }

    }

}