package com.yeab.esnapp.ui.order

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.yeab.esnapp.databinding.ActivityAutoCaptureBinding
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.util.IntentKeys
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.compareTo
import kotlin.div
import kotlin.ranges.rangeTo
import kotlin.text.compareTo
import kotlin.text.format
import kotlin.text.toDouble
import kotlin.text.toInt
import kotlin.times
import kotlin.toString

class AutoCaptureActivity : BaseActivity() {

    private lateinit var binding: ActivityAutoCaptureBinding
    private lateinit var cameraExecutor: ExecutorService

    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Çift çekimi önlemek için kilit
    @Volatile
    private var isLocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityAutoCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isLocked = false

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions.launch(REQUIRED_PERMISSIONS)
        }

        binding.tvDetectedTextStatus.visibility = View.GONE

        // Manuel butona basılırsa da kilitleyip çekelim
        binding.btnManualCapture.setOnClickListener {
            if (!isLocked) {
                lockAndCapture("Manuel Çekim")
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // 1. Önizleme (Preview)
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // 2. Fotoğraf Çekme (Capture)
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // 3. Görüntü Analizi (OCR)
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        cameraExecutor,
                        TextAnalyzer(
                            onMatch = { text ->
                                runOnUiThread {

                                    binding.tvDetectedTextStatus.visibility = View.VISIBLE
                                    binding.tvDetectedText.text = text ?: "yazı bulunamadı" }
                            },
                            onNoMatch = {
                                runOnUiThread {
                                    binding.tvDetectedTextStatus.visibility = View.GONE
                                    binding.tvDetectedText.text = ""
                                    // Alternatif: binding.tvDetectedTextStatus.isVisible = false
                                }
                            }
                        )
                    )
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Önceki bağlantıları temizle ve yenilerini bağla
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Kamera başlatılamadı", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // İşlem başladığı an sistemi kilitler
    private fun lockAndCapture(statusMessage: String) {
        if (isLocked) return // Zaten kilitliyse çık
        isLocked = true // Kilitle

        runOnUiThread {

            // 3. Fotoğrafı çek
            takePhoto()
        }
    }

    // Kotlin
// 'app/src/main/java/com/yeab/esnapp/ui/order/AutoCaptureActivity.kt'
    private fun takePhoto() {
        // Algılanan metni al ve kontrol et
        val recognized = binding.tvDetectedText.text?.toString()?.trim()
        if (recognized.isNullOrBlank()) {
            Toast.makeText(this, "Ekranda herhangi bir yazı bulunamadı.", Toast.LENGTH_SHORT).show()
            // İşlem devam etmesin, kilidi açalım ki kullanıcı tekrar deneyebilsin
            isLocked = false
            return
        }

        val imageCapture = imageCapture ?: return

        val photoFile = File(
            externalMediaDirs.firstOrNull(),
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Fotoğraf hatası: ${exc.message}", exc)
                    isLocked = false
                    runOnUiThread {
                        binding.txtStatus.text = "Hata oluştu, tekrar deneyin."
                    }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)

                    val resultIntent = Intent().apply {
                        putExtra("captured_image_uri", savedUri.toString())
                        // Metin boş değil, geri gönder
                        putExtra(IntentKeys.RECOGNIZED_TEXT, recognized)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        )
    }


    // Kotlin
// 'app/src/main/java/com/yeab/esnapp/ui/order/AutoCaptureActivity.kt'
    private class TextAnalyzer(
        private val onMatch: (String?) -> Unit,
        private val onNoMatch: () -> Unit
    ) : ImageAnalysis.Analyzer {

        private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // Histerezis sayaçları
        private var consecutiveMatches = 0
        private var consecutiveMisses = 0

        // Eşikler (ihtiyaca göre ayarlayın)
        private val minConsecutiveMatches = 2
        private val minConsecutiveMisses = 3

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image ?: return imageProxy.close()
            val rotation = imageProxy.imageInfo.rotationDegrees
            val width = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
            val height = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

            // UI’daki yeşil çerçeveye yakın oranlar (gerekirse güncelleyin)
            val boxWidth = (width * 0.65).toInt()
            val boxHeight = (height * 0.25).toInt()
            val cx = width / 2
            val cy = height / 2
            val scanRect = Rect(
                cx - (boxWidth / 2),
                cy - (boxHeight / 2),
                cx + (boxWidth / 2),
                cy + (boxHeight / 2)
            )

            val image = InputImage.fromMediaImage(mediaImage, rotation)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    var insideFound = false
                    var insideText: String? = null

                    for (block in visionText.textBlocks) {
                        val box = block.boundingBox ?: continue
                        // Basit merkez testi yeterli olabilir; isConfidentlyInside’ı çok katı buluyorsanız bunu kullanın:
                        val cxText = (box.left + box.right) / 2
                        val cyText = (box.top + box.bottom) / 2
                        val centerInside = cxText in scanRect.left..scanRect.right &&
                                cyText in scanRect.top..scanRect.bottom

                        if (centerInside) {
                            insideFound = true
                            insideText = block.text
                            break
                        }
                    }

                    if (insideFound) {
                        consecutiveMatches++
                        consecutiveMisses = 0
                        if (consecutiveMatches >= minConsecutiveMatches) {
                            onMatch(insideText)
                        }
                    } else {
                        consecutiveMisses++
                        if (consecutiveMisses >= minConsecutiveMisses) {
                            consecutiveMatches = 0
                            onNoMatch()
                        }
                    }

                    imageProxy.close()
                }
                .addOnFailureListener {
                    consecutiveMisses++
                    if (consecutiveMisses >= minConsecutiveMisses) {
                        consecutiveMatches = 0
                        onNoMatch()
                    }
                    imageProxy.close()
                }
        }
    }


    // --- İZİN YÖNETİMİ ---
    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            Toast.makeText(this, "Kamera izni verilmedi.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "AutoCapture"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }


}