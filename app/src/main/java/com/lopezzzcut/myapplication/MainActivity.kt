package com.lopezzzcut.myapplication

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.lopezzzcut.myapplication.ui.DetectorFacial
import com.lopezzzcut.myapplication.ui.theme.MyApplicationTheme
import java.util.concurrent.Executors
import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.core.graphics.minus
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceContour.LEFT_EYE
import com.google.mlkit.vision.face.FaceContour.RIGHT_EYE
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.face.FaceLandmark.MOUTH_BOTTOM
import java.io.ByteArrayOutputStream


class MainActivity : ComponentActivity() {
    private val REQUEST_CAMERA_PERMISSION = 1

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        } else {
            // Permiso concedido, continúa con la inicialización de la cámara
            setContent {
                MyApplicationTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        CameraScreen()
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido, continúa con la inicialización de la cámara
                setContent {
                    MyApplicationTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            CameraScreen()
                        }
                    }
                }
            } else {
                // Permiso denegado, maneja esto según tus necesidades
                Log.e("MainActivity", "Permiso de cámara denegado")
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun Greeting(name: String) {
    val context = LocalContext.current
    val imageCapture = remember {
        context.display?.let {
            ImageCapture.Builder()
            .setTargetRotation(it.rotation)
            .build()
        }
    }
    val detector = DetectorFacial()

    Text(
        text = "Hello $name!",

    )

    Button(onClick = {
        imageCapture?.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // Aquí tienes tu ImageProxy.
                    // Puedes pasarlo a la función analyze().
                    detector.analyze(image)
                    Log.d("MainActivity", "onCaptureSuccess")
                }

                override fun onError(exception: ImageCaptureException) {
                    // Maneja cualquier error aquí.
                    Log.d("MainActivity", "onError", exception)
                }
            }
        )
    }) {
        Text("Capture Image")
    }
}
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val previewView: PreviewView = remember { PreviewView(context) }
    val cameraController = remember { LifecycleCameraController(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    cameraController.bindToLifecycle(lifecycleOwner)
    cameraController.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    previewView.controller = cameraController

    val executor = remember { Executors.newSingleThreadExecutor() }
    val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()
    val textRecognizer = remember { FaceDetection.getClient(highAccuracyOpts) }

    var text by rememberSaveable {
        mutableStateOf("")
    }
    var isLoading by remember { mutableStateOf(false) }
    var faceImage by remember { mutableStateOf<ImageBitmap?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Show loading indicator when isLoading is true
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(50.dp)
                    .align(Alignment.Center)
            )
        } else {
            IconButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                onClick = {
                    isLoading = true
                    cameraController.setImageAnalysisAnalyzer(executor) { imageProxy ->
                        imageProxy.image?.let { image ->
                            val img = InputImage.fromMediaImage(
                                image,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            textRecognizer.process(img).addOnSuccessListener { faces ->
                                isLoading = false

                                if (faces.isNotEmpty()) {
                                    val face = faces[0] // Assuming only one face is detected

                                    // Get face shape information
                                    val faceShape = getFaceShape(face)

                                    // Process face shape information (you can update UI accordingly)
                                    text = "Face Shape: $faceShape"

                                    // Draw landmarks and face shape on the image
                                    faceImage = drawFaceLandmarks(image, face)
                                } else {
                                    text = "No face detected"
                                }

                                cameraController.clearImageAnalysisAnalyzer()
                                imageProxy.close()
                            }
                        }
                    }
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_background),
                    contentDescription = "",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(54.dp)
                )
            }
        }
    }

    if (text.isNotEmpty()) {
        Dialog(onDismissRequest = { text = "" }) {
            Card() { // Ajusta el tamaño aquí
                Column {
                    Text(
                        text = text,
                        modifier = Modifier.padding(32.dp),
                        fontSize = 24.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    faceImage?.let {
                        Image(bitmap = it, contentDescription = "Face with landmarks", modifier = Modifier.fillMaxWidth().size(300.dp))
                    }
                    Button(
                        onClick = { text = "" },
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
fun Bitmap.rotate(degree: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degree)
    return Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
}

fun drawFaceLandmarks(image: Image, face: Face): ImageBitmap {
    var bitmap = image.toBitmap()
    bitmap = bitmap.rotate(270f) // Ajusta el ángulo de rotación si es necesario

    val canvas = Canvas(bitmap)
    val paint1 = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    val paint2 = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    face.boundingBox.let {
        val rect = RectF(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
        canvas.drawRect(rect, paint2)
    }
    // Draw landmarks
    for (landmark in face.allLandmarks) {
        val point = landmark.position
        canvas.drawCircle(point.x, point.y, 10f, paint1)
    }

    // Draw face contour
    val path = Path()
    for (i in 0 until (face.getContour(FaceContour.FACE)?.points?.size ?: 0)) {
        val point = face.getContour(FaceContour.FACE)?.points?.get(i)
        if (i == 0) {
            if (point != null) {
                path.moveTo(point.x, point.y)
            }
        } else {
            if (point != null) {
                path.lineTo(point.x, point.y)
            }
        }
    }
    path.close()
    canvas.drawPath(path, paint1)

    // Convert the Bitmap to ImageBitmap for Compose
    return bitmap.asImageBitmap()
}

fun Image.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer // Y
    val uBuffer = planes[1].buffer // U
    val vBuffer = planes[2].buffer // V

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    //U and V are swapped
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size).copy(Bitmap.Config.ARGB_8888, true)
}

fun getFaceShape(face: Face): String {
    val leftEye = face.getLandmark(LEFT_EYE)?.position
    val rightEye = face.getLandmark(RIGHT_EYE)?.position
    val mouth = face.getLandmark(MOUTH_BOTTOM)?.position

    if (leftEye != null && rightEye != null && mouth != null) {
        val distanceEyeToEye = calculateDistance(leftEye, rightEye)
        val distanceEyeToMouth = calculateDistance(leftEye, mouth)
        val jawWidth = face.boundingBox.width().toFloat()
        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)
        val cheekWidth = calculateDistance(leftCheek!!.position, rightCheek!!.position)
        val faceLength = face.boundingBox.height().toFloat()
        val ratio = cheekWidth / faceLength
        Log.d("MainActivity", "ratio: $ratio")
        Log.d("MainActivity", "jawWidth: $cheekWidth")
        Log.d("MainActivity", "faceLength: $faceLength")
        return when {
            isSquare(cheekWidth, cheekWidth) -> "Cuadrada"
            isRound(cheekWidth) -> "Redonda"
            isOval(cheekWidth, faceLength) -> "Ovalada"
            isLong(cheekWidth) -> "Alargada"
            else -> "Unknown"
        }
    }

    return "Unknown"
}

fun calculateDistance(point1: PointF, point2: PointF): Float {
    val dx = point1.x - point2.x
    val dy = point1.y - point2.y
    return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
}

fun isSquare(cheekWidth: Float, faceLength: Float): Boolean {
    val ratio = cheekWidth / faceLength
    return ratio > 0.40 && ratio < 47 && cheekWidth >100
}

fun isRound(cheekWidth: Float, ): Boolean {
    return  cheekWidth  > 100
}

fun isOval(cheekWidth: Float, faceLength: Float): Boolean {
    val ratio = cheekWidth / faceLength

    return ratio > 0.35 && ratio < 50 && cheekWidth < 100
}

fun isLong( cheekWidth: Float): Boolean {
    return cheekWidth < 100
}
fun Float.squared() = this * this


@RequiresApi(Build.VERSION_CODES.R)
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}