package com.yeab.esnapp.ui.order

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.database.*
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivitySearchOrderBinding
import com.yeab.esnapp.model.Order
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.ImageSimilarityUtils
import com.yeab.esnapp.util.IntentKeys
import java.net.URL
import org.opencv.android.OpenCVLoader
import android.util.Log

class SearchOrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchOrderBinding
    private var merchantUid: String? = null
    private val dbRef = FirebaseDatabase.getInstance().reference

    // Kamera sonucu
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val bitmap = result.data!!.extras?.get("data") as? Bitmap
                if (bitmap != null) {
                    searchByImage(bitmap)
                }
            }
        }

    // Kamera izni sonucu
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openCameraForSearch()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.error_camera_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (OpenCVLoader.initLocal()) {
            Log.i("OpenCV", "OpenCV loaded successfully")
        } else {
            Log.e("OpenCV", "OpenCV initialization failed")
            Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_LONG).show()
            // İstersen burada return deyip image search’ü kapatabilirsin
        }

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        binding.btnSearchByImage.setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        binding.btnSearchByPhone.setOnClickListener {
            val i = Intent(this, SearchByPhoneActivity::class.java)
            i.putExtra(IntentKeys.MERCHANT_UID, merchantUid)
            startActivity(i)
        }
    }

    private fun checkCameraPermissionAndOpen() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            openCameraForSearch()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCameraForSearch() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    /**
     * Kameradan alınan bitmap ile Firebase'teki ProductImageUrl görsellerini
     * benzerlik hesabı yaparak karşılaştırır.
     */
    private fun searchByImage(capturedBitmap: Bitmap) {
        val uid = merchantUid ?: return

        dbRef.child(FirebasePaths.ORDERS_ROOT)
            .child(FirebasePaths.ORDERS_MERCHANT_ORDERS)
            .child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    // Snapshot geldi, network + görsel işleri için background thread
                    Thread {

                        var bestPhone: String? = null
                        var bestOrderId: String? = null
                        var bestProductName: String? = null
                        var bestScore = 0.0

                        // Her telefon node'u
                        for (phoneSnap in snapshot.children) {
                            val phoneKey = phoneSnap.key ?: continue

                            // Her order
                            for (orderSnap in phoneSnap.children) {
                                val orderId = orderSnap.key ?: continue
                                val order = orderSnap.getValue(Order::class.java) ?: continue

                                // Java getter -> Kotlin property:
                                // isFinished, productImageUrl, productName
                                if (order.isFinished) continue
                                if (order.productImageUrl.isNullOrEmpty()) continue

                                val remoteBitmap =
                                    loadBitmapFromUrl(order.productImageUrl!!)
                                if (remoteBitmap != null) {
                                    val score = ImageSimilarityUtils.calculateSimilarity(
                                        capturedBitmap,
                                        remoteBitmap
                                    )
                                    if (score > bestScore) {
                                        bestScore = score
                                        bestPhone = phoneKey
                                        bestOrderId = orderId
                                        bestProductName = order.productName
                                    }
                                    remoteBitmap.recycle()
                                }
                            }
                        }

                        val threshold = 0.2

                        runOnUiThread {
                            if (bestPhone != null && bestOrderId != null && bestScore >= threshold) {
                                Toast.makeText(
                                    this@SearchOrderActivity,
                                    getString(
                                        R.string.info_image_match_found,
                                        (bestScore * 100).toInt()
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()

                                val i = Intent(
                                    this@SearchOrderActivity,
                                    OrderStatusUpdateActivity::class.java
                                )
                                i.putExtra(IntentKeys.MERCHANT_UID, uid)
                                i.putExtra(IntentKeys.PHONE, bestPhone)
                                i.putExtra(IntentKeys.ORDER_ID, bestOrderId)
                                i.putExtra(
                                    IntentKeys.PRODUCT_NAME,
                                    bestProductName
                                ) // Eşleşen ürün adı
                                startActivity(i)
                            } else {
                                Toast.makeText(
                                    this@SearchOrderActivity,
                                    getString(R.string.error_no_image_match).plus("..: ").plus(bestScore),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                    }.start()
                }

                override fun onCancelled(error: DatabaseError) {
                    runOnUiThread {
                        Toast.makeText(
                            this@SearchOrderActivity,
                            error.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
    }

    /**
     * URL'den bitmap indirir. Hata olursa null döner.
     * Bu method mutlaka background thread'de çağrılmalı.
     */
    private fun loadBitmapFromUrl(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection()
            connection.connect()
            val input = connection.getInputStream()
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
