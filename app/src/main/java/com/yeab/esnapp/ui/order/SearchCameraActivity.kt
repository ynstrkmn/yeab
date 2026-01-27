package com.yeab.esnapp.ui.order

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.yeab.esnapp.databinding.ActivitySearchCameraBinding
import com.yeab.esnapp.ui.base.BaseActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SearchCameraActivity : BaseActivity() {

    private lateinit var binding: ActivitySearchCameraBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySearchCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions.launch(REQUIRED_PERMISSIONS)
        }

        binding.btnCapture.setOnClickListener {
            takePhotoAndProcess()
        }

        binding.btnClose.setOnClickListener {
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Kamera başlatılamadı", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhotoAndProcess() {
        val imageCapture = imageCapture ?: return

        Toast.makeText(this, "İşleniyor...", Toast.LENGTH_SHORT).show()
        binding.btnCapture.isEnabled = false

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
                    binding.btnCapture.isEnabled = true
                    Toast.makeText(baseContext, "Fotoğraf hatası: ${exc.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Fotoğraf çekildi, şimdi kırpıp kaydedeceğiz
                    cropAndReturnImage(photoFile)
                }
            }
        )
    }

    // --- YENİ FONKSİYON: SADECE RESMİ KIRP VE GÖNDER (OCR YOK) ---
    private fun cropAndReturnImage(photoFile: File) {
        try {
            // 1. Bitmap Yükle
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)

            // 2. Döndür
            val rotatedBitmap = fixRotation(photoFile.absolutePath, bitmap)

            // 3. Kırpma Oranları (ScannerOverlayView ile aynı)
            val width = rotatedBitmap.width
            val height = rotatedBitmap.height
            val cropWidth = (width * 0.70).toInt()
            val cropHeight = (height * 0.25).toInt()
            val cx = width / 2
            val cy = height / 2

            val startX = (cx - (cropWidth / 2)).coerceAtLeast(0)
            val startY = (cy - (cropHeight / 2)).coerceAtLeast(0)
            val finalWidth = cropWidth.coerceAtMost(width - startX)
            val finalHeight = cropHeight.coerceAtMost(height - startY)

            // 4. Kırpılmış Bitmap'i oluştur
            val croppedBitmap = Bitmap.createBitmap(
                rotatedBitmap,
                startX,
                startY,
                finalWidth,
                finalHeight
            )

            // 5. Kırpılmış resmi YENİ BİR DOSYAYA kaydet
            val croppedFile = File(externalMediaDirs.firstOrNull(), "cropped_${System.currentTimeMillis()}.jpg")
            val outStream = FileOutputStream(croppedFile)
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
            outStream.flush()
            outStream.close()

            // 6. Yeni dosyanın URI'sini geri döndür
            val savedUri = Uri.fromFile(croppedFile)
            val resultIntent = Intent().apply {
                putExtra("captured_image_uri", savedUri.toString())
            }
            setResult(RESULT_OK, resultIntent)
            finish()

        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                binding.btnCapture.isEnabled = true
                Toast.makeText(this, "Resim işleme hatası", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fixRotation(photoPath: String, bitmap: Bitmap): Bitmap {
        try {
            val ei = androidx.exifinterface.media.ExifInterface(photoPath)
            val orientation = ei.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED
            )

            return when (orientation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            return bitmap
        }
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (allPermissionsGranted()) startCamera() else {
            Toast.makeText(this, "İzin gerekli", Toast.LENGTH_SHORT).show()
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
        private const val TAG = "SearchCamera"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}