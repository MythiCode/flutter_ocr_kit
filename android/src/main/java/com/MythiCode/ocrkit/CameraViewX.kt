package com.MythiCode.ocrkit

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.ExifInterface
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.MythiCode.ocrkit.Model.CornerPointModel
import com.MythiCode.ocrkit.Model.LineModel
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class CameraViewX(
    private val activity: Activity,
    private val flutterMethodListener: FlutterMethodListener
) : CameraViewInterface {
    private var imageCapture: ImageCapture? = null
    private var previewFlashMode = 0.toChar()
    private var isTakePictureMode = false
    private var isScanningText = false
    private var userCameraSelector = 0
    private var displaySize: Point? = null
    private var textRecognizer: TextRecognizer? = null
    private var previewView: PreviewView? = null
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var isCameraVisible = true
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraSelector: CameraSelector? = null
    private var preview: Preview? = null
    private var optimalPreviewSize: Size? = null
    private var displayRotation = 0
    private var barcodeFrame: BarcodeFrame? = null
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    override fun initCamera(
        relativeLayout: RelativeLayout,
        hasTextReader: Boolean,
        flashMode: Char,
        isFillScale: Boolean,
        isTakePictureMode: Boolean,
        isScanningText: Boolean
    ) {
        previewFlashMode = flashMode
        this.isTakePictureMode = isTakePictureMode
        this.isScanningText = isScanningText
        userCameraSelector = 0
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        displaySize = Point()
        activity.windowManager.defaultDisplay.getSize(displaySize)
        if (isFillScale) //fill
            relativeLayout.layoutParams = FrameLayout.LayoutParams(
                displaySize!!.x,
                displaySize!!.y
            )

//        textureView = new AutoFitTextureView(activity);
//        textureView.setLayoutParams(new FrameLayout.LayoutParams(
//                ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.MATCH_PARENT));
        previewView = PreviewView(activity)
        previewView!!.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        barcodeFrame = BarcodeFrame(activity)
        barcodeFrame!!.setFrameColor(Color.GREEN)
        barcodeFrame!!.setLaserColor(Color.RED)
        relativeLayout.addView(previewView)
        relativeLayout.addView(barcodeFrame)
        startCamera()
        Log.e(ContentValues.TAG, "initCamera:")
    }

    private val flashMode: Int
        get() = when (previewFlashMode) {
            'O' -> ImageCapture.FLASH_MODE_ON
            'F' -> ImageCapture.FLASH_MODE_OFF
            'A' -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_AUTO
        }

    private fun prepareOptimalSize() {
        val width = previewView!!.width
        val height = previewView!!.height
        val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    if (userCameraSelector == 0) continue
                } else {
                    if (userCameraSelector == 1) continue
                }
                val map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                ) ?: continue


                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                displayRotation = activity.windowManager.defaultDisplay.rotation
                val sensorOrientation =
                    characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                var swappedDimensions = false
                when (displayRotation) {
                    Surface.ROTATION_0, Surface.ROTATION_180 -> if (sensorOrientation == 90 || sensorOrientation == 270) {
                        swappedDimensions = true
                    }
                    Surface.ROTATION_90, Surface.ROTATION_270 -> if (sensorOrientation == 0 || sensorOrientation == 180) {
                        swappedDimensions = true
                    }
                    else -> Log.e(
                        ContentValues.TAG,
                        "Display rotation is invalid: $displayRotation"
                    )
                }
                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height
                var maxPreviewWidth = displaySize!!.x
                var maxPreviewHeight = displaySize!!.y
                if (swappedDimensions) {
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width
                    maxPreviewWidth = displaySize!!.y
                    maxPreviewHeight = displaySize!!.x
                }
                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH
                }
                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                val previewSize = chooseOptimalSize(
                    map.getOutputSizes(
                        SurfaceTexture::class.java
                    ),
                    rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                    maxPreviewHeight
                )
                val orientation = activity.resources.configuration.orientation
                optimalPreviewSize = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Size(previewSize.width, previewSize.height)
                } else {
                    Size(previewSize.height, previewSize.width)
                }
                return
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            e.printStackTrace()
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture!!.addListener({
            try {
                cameraProvider = cameraProviderFuture!!.get()
                prepareOptimalSize()
                preview = Preview.Builder()
                    .setTargetResolution(
                        Size(
                            optimalPreviewSize!!.width,
                            optimalPreviewSize!!.height
                        )
                    )
                    .build()
                preview!!.setSurfaceProvider(previewView!!.surfaceProvider)


                imageCapture = ImageCapture.Builder()
                    .setFlashMode(flashMode)
                    .setTargetResolution(
                        Size(
                            optimalPreviewSize!!.width,
                            optimalPreviewSize!!.height
                        )
                    )
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraSelector =
                    if (userCameraSelector == 0) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                bindCamera()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun setFlashBarcodeReader() {
        if (camera != null) {
            camera!!.cameraControl.enableTorch(previewFlashMode == 'O')
        }
    }

    private fun bindCamera() {
        cameraProvider!!.unbind()
        camera = cameraProvider!!.bindToLifecycle(
            (activity as LifecycleOwner), cameraSelector!!, preview, imageCapture
        )
        setFlashBarcodeReader()
    }

    override fun setCameraVisible(isCameraVisible: Boolean) {
        if (isCameraVisible != this.isCameraVisible) {
            this.isCameraVisible = isCameraVisible
            if (isCameraVisible) resumeCamera2() else pauseCamera2()
        }
    }

    override fun changeFlashMode(newPreviewFlashMode: Char) {
        previewFlashMode = newPreviewFlashMode
        imageCapture!!.flashMode = flashMode
        setFlashBarcodeReader()
    }

    fun cropImage(
        bitmap: Bitmap,
        rotationDegree: Int,
        xOffset: Int,
        yOffset: Int,
        cropWidth: Int,
        cropHeight: Int
    ): Bitmap {
        // 2 - Rotate the Bitmap
        var bitmap = bitmap
        if (rotationDegree != 0) {
            val rotationMatrix = Matrix()
            rotationMatrix.postRotate(rotationDegree.toFloat())
            bitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotationMatrix, true)
        }

        // 3 - Crop the Bitmap
        bitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, cropWidth, cropHeight)
        return bitmap
    }

    private fun convertExifOreintationToRotation(orientation: Int): Int {
        var rotation = 0
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotation = 90
            ExifInterface.ORIENTATION_ROTATE_180 -> rotation = 180
            ExifInterface.ORIENTATION_ROTATE_270 -> rotation = 270
        }
        return rotation
    }

    private val tag = "ANALYZER"

    override fun takePicture(result: MethodChannel.Result) {
        // Set up image capture listener, which is triggered after photo has
        // been taken
        Log.e(tag, "takePicture $result")

        imageCapture!!.takePicture(
            ContextCompat.getMainExecutor(activity),
            object : ImageCapture.OnImageCapturedCallback() {

                @SuppressLint("UnsafeOptInUsageError")
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    // Use the image, then make sure to close it.
                    val mediaImage = imageProxy.image
                    val recognizer: TextRecognizer =
                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    if (mediaImage != null) {
                        val image =
                            InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                        // Pass image to an ML Kit Vision API
                        recognizer.process(image)
                            .addOnSuccessListener {
                                // Task completed successfully
                                for (block in it.textBlocks) {
                                    val blockText = block.text
                                    val blockCornerPoints: Array<Point> = block.cornerPoints!!
                                    val blockFrame: Rect = block.boundingBox!!
                                    Log.w(tag, "BlockText $blockText")
                                    processText(it, "")
                                    for (line in block.lines) {
                                        val lineText = line.text
                                        val lineCornerPoints: Array<Point> = line.cornerPoints!!
                                        val lineFrame: Rect = line.boundingBox!!
                                        Log.w(tag, "LineText $lineText")
                                        for (element in line.elements) {
                                            val elementText = element.text
                                            val elementCornerPoints: Array<Point> =
                                                element.cornerPoints!!
                                            val elementFrame: Rect = element.boundingBox!!
                                            // Log.d(tag, "ElementText $elementText")
                                            for (el in elementCornerPoints) {
                                                Log.d(
                                                    tag,
                                                    "elementText $elementText  cornerPoints : X:${el.x} Y:${el.y}"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener {
                                // Task failed with an exception
                                Log.e(tag, "Failed to load the image")
                            }
                    }
                    imageProxy.close()
                }


                override fun onError(exception: ImageCaptureException) {
                    Log.e(tag, "Error to load the image1 ${exception.imageCaptureError}")
                    Log.e(tag, "Error to load the image2 ${exception.message}")
                    Log.e(tag, "Error to load the image3 ${exception.cause}")
                }
            })

    }

    override fun pauseCamera() {}

    override fun resumeCamera() {
        if (isCameraVisible) {
            setFlashBarcodeReader()
        }
    }

    override fun dispose() {}

    override fun setScanForText(isScanningText: Boolean) {
        this.isScanningText = isScanningText
    }

    override fun processImageFromPath(path: String) {
        Log.d(tag, "processImageFromPath")
        try {
            val bitmap = BitmapFactory.decodeFile(path)
            val exif = ExifInterface(path)
            val rotation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val recognizer: TextRecognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(inputImage)
                .addOnSuccessListener {
                    // Task completed successfully

                    val map: MutableMap<String, String> = mutableMapOf()
                    val listPoints: ArrayList<MutableMap<String, String>> = ArrayList()

                    map["text"] = it.text
                    map["path"] = path
                    map["orientation"] = "$rotation"

                    for (block in it.textBlocks) {

                        processText(it, "")
                        for (line in block.lines) {
                            for (element in line.elements) {
                                val value: MutableMap<String, String> = mutableMapOf()
                                val elementText = element.text
                                val elementCornerPoints: Array<Point> =
                                    element.cornerPoints!!
                                value["text"] = elementText
                                value["cornerPoints"] = elementCornerPoints.toString()

                                listPoints.add(value)
                            }
                        }

                    }
                    map["values"] = listPoints.toString()

                  //  Log.d(tag, "Map $map")
                }
                .addOnFailureListener {
                    // Task failed with an exception
                    Log.e(tag, "Failed to load the image ${it.cause}")
                    Log.e(tag, "Failed to load the image ${it.message}")
                }

        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(tag, "Error to load the image From Path: $e")
        }
    }

    override fun processImageFromPathWithoutView(path: String) {
        Log.d(tag, "processImageFromPathWithoutView")
        try {
            val bitmap = BitmapFactory.decodeFile(path)
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val recognizer: TextRecognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(inputImage)
                .addOnSuccessListener {
                    // Task completed successfully
                    Log.d(tag, "IT ${it.text}")

                    val map: MutableMap<String, String> = mutableMapOf()
                    val listPoints: ArrayList<MutableMap<String, String>> = ArrayList()

                    map["text"] = it.text
                    map["path"] = path
                    map["orientation"] = "0"

                    for (block in it.textBlocks) {

                        processText(it, "")
                        for (line in block.lines) {
                            for (element in line.elements) {
                                val value: MutableMap<String, String> = mutableMapOf()
                                val elementText = element.text
                                val elementCornerPoints: Array<Point> =
                                    element.cornerPoints!!
                                value["text"] = elementText
                                value["cornerPoints"] = elementCornerPoints.toString()
                                listPoints.add(value)
                            }
                        }

                    }
                    map["values"] = listPoints.toString()

                    Log.d(tag, "MAP $map")
                }
                .addOnFailureListener {
                    // Task failed with an exception
                    Log.e(tag, "Failed to load the image ${it.cause}")
                    Log.e(tag, "Failed to load the image ${it.message}")
                }

        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(tag, "Error to load the image From Path: $e")
        }
    }

    private fun pauseCamera2() {
        cameraProvider!!.unbindAll()
        if (textRecognizer != null) {
            textRecognizer!!.close()
            textRecognizer = null
        }
    }

    private fun resumeCamera2() {
        if (isCameraVisible) {
            if (textRecognizer == null) textRecognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            startCamera()
        }
    }

    private val pictureFile: File
        get() = File(activity.cacheDir, "pic.jpg")

    private fun processText(text: Text, path: String) {
        val lineModels: MutableList<LineModel> = ArrayList()
        for (b in text.textBlocks) for (line in b.lines) {
            val lineModel = LineModel(line.text)
            for (p in line.cornerPoints!!) {
                lineModel.cornerPoints.add(CornerPointModel(p.x.toFloat(), p.y.toFloat()))
            }
            lineModels.add(lineModel)
        }
        val gson = Gson()
        gson.toJson(lineModels)
        flutterMethodListener.onTextRead(text.text, Gson().toJson(lineModels), path)
    }

    private inner class BarcodeAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            //   if (!isScanningText) return;
//            @SuppressLint("UnsafeExperimentalUsageError") Image mediaImage = imageProxy.getImage();
//            if (mediaImage != null) {
//                InputImage image =
//                        InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
//
////                textRecognizer.process(image)
////                        .addOnSuccessListener(new OnSuccessListener<Text>() {
////                            @Override
////                            public void onSuccess(Text text) {
////                                processText(text, null);
////                            }
////                        })
////                        .addOnFailureListener(new OnFailureListener() {
////                            @Override
////                            public void onFailure(@NonNull Exception e) {
////                                flutterMethodListener.onTextRead("Error in reading" + e.getMessage(), null, null);
////                            }
////                        })
////                        .addOnCompleteListener(new OnCompleteListener<Text>() {
////                            @Override
////                            public void onComplete(@NonNull Task<Text> task) {
////                                imageProxy.close();
////                            }
////                        });
//            }
        }
    }

    companion object {
        private const val MAX_PREVIEW_WIDTH = 1920
        private const val MAX_PREVIEW_HEIGHT = 1080
        private fun chooseOptimalSize(
            choices: Array<Size>,
            textureViewWidth: Int,
            textureViewHeight: Int,
            maxWidth: Int,
            maxHeight: Int
        ): Size {

            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough: MutableList<Size> = ArrayList()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough: MutableList<Size> = ArrayList()
            //        int w = aspectRatio.getWidth();
//        int h = aspectRatio.getHeight();
            val w = 16
            val h = 9
            for (option in choices) {
                if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * h / w) {
                    if (option.width >= textureViewWidth &&
                        option.height >= textureViewHeight
                    ) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            return if (bigEnough.size > 0) {
                Collections.min(bigEnough, CameraView2.CompareSizesByArea())
            } else if (notBigEnough.size > 0) {
                Collections.max(notBigEnough, CameraView2.CompareSizesByArea())
            } else {
                Log.e(ContentValues.TAG, "Couldn't find any suitable preview size")
                choices[0]
            }
        }
    }

    init {
        Log.e(ContentValues.TAG, "CameraViewX")
    }

}