package com.yeab.esnapp.ui.order

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
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

class AutoCaptureActivity : BaseActivity() {

    private lateinit var binding: ActivityAutoCaptureBinding
    private lateinit var cameraExecutor: ExecutorService

    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Aranacak numara (Örn: 106)
    private var targetOrderNumber: String = ""

    // Çift çekimi önlemek için kilit
    @Volatile
    private var isLocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityAutoCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent'ten numarayı al
        targetOrderNumber = intent.getStringExtra(IntentKeys.ORDER_NUMBER) ?: ""
        binding.txtTargetOrderNumber.text = targetOrderNumber

        isLocked = false

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions.launch(REQUIRED_PERMISSIONS)
        }

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
                    it.setAnalyzer(cameraExecutor, TextAnalyzer(targetOrderNumber) { found ->
                        if (found) {
                            // Bulundu!
                            lockAndCapture("YAKALANDI: $targetOrderNumber")
                        }
                    })
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
            // 1. KRİTİK ADIM: Analizi kameradan sök (Gözü kapat)
            // Böylece kamera artık okuma yapamaz ve ikinci kez tetiklenmez.
            try {
                cameraProvider?.unbind(imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Kullanıcıya bilgi ver
            binding.txtStatus.text = statusMessage
            binding.txtStatus.setTextColor(android.graphics.Color.GREEN)

            // 3. Fotoğrafı çek
            takePhoto()
        }
    }

    private fun takePhoto() {
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
                    // Hata olursa kilidi açalım ki tekrar denesin
                    isLocked = false
                    runOnUiThread {
                        binding.txtStatus.text = "Hata oluştu, tekrar deneyin."
                    }
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)

                    // Sonucu hazırla ve dön
                    val resultIntent = Intent().apply {
                        putExtra("captured_image_uri", savedUri.toString())
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        )
    }

    // --- KATI KURAL UYGULAYAN ANALİZ SINIFI ---
    private class TextAnalyzer(
        private val targetText: String,
        private val onFound: (Boolean) -> Unit
    ) : ImageAnalysis.Analyzer {

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val rotation = imageProxy.imageInfo.rotationDegrees
                // Cihazın tutuş yönüne göre genişlik/yükseklik ayarı
                val width = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
                val height = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

                // --- TARAMA ALANI (KUTU) ---
                // Ekranın tam ortasında sanal bir kutu oluşturuyoruz.
                // XML'deki görsel kutuyla (280dp x 120dp) uyumlu olması için oranlar:
                // Genişlik %65, Yükseklik %25 (Biraz esneme payı ile)
                val boxWidth = (width * 0.65).toInt()
                val boxHeight = (height * 0.25).toInt()

                val cx = width / 2
                val cy = height / 2

                // Kutunun koordinatlarını hesapla
                val scanRect = Rect(
                    cx - (boxWidth / 2),
                    cy - (boxHeight / 2),
                    cx + (boxWidth / 2),
                    cy + (boxHeight / 2)
                )

                val image = InputImage.fromMediaImage(mediaImage, rotation)

                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        for (block in visionText.textBlocks) {
                            val box = block.boundingBox

                            if (box != null) {
                                // 1. KONTROL: Metin aranan numarayı içeriyor mu?
                                if (block.text.contains(targetText)) {

                                    // 2. KONTROL: KATI KAPSAMA (CONTAINMENT)
                                    // Yazının tamamı (%100'ü) yeşil kutunun içinde mi?
                                    if (isCompletelyInside(scanRect, box)) {
                                        onFound(true)
                                        imageProxy.close() // Bulduk, kaynağı serbest bırak
                                        return@addOnSuccessListener
                                    }
                                }
                            }
                        }
                        // Bulamadıysak kapat, bir sonraki kareye geç
                        imageProxy.close()
                    }
                    .addOnFailureListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }

        // --- YAZI TAMAMEN KUTUNUN İÇİNDE Mİ? ---
        private fun isCompletelyInside(scanRect: Rect, textRect: Rect): Boolean {
            // Text Sol kenarı >= Kutu Sol kenarı
            // Text Sağ kenarı <= Kutu Sağ kenarı
            // Text Üst kenarı >= Kutu Üst kenarı
            // Text Alt kenarı <= Kutu Alt kenarı

            return textRect.left >= scanRect.left &&
                    textRect.top >= scanRect.top &&
                    textRect.right <= scanRect.right &&
                    textRect.bottom <= scanRect.bottom
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