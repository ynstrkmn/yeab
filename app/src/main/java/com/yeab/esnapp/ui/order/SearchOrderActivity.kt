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
    // kotlin
    private fun searchByImage(capturedBitmap: Bitmap) {
        val uid = merchantUid ?: return

        dbRef.child(FirebasePaths.ORDERS_ROOT)
            .child(FirebasePaths.ORDERS_MERCHANT_ORDERS)
            .child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Eşik: ImageSimilarityUtils Integer Hamming threshold kullanır
                    val threshold = 20

                    ImageSimilarityUtils.calculateSimilarity(
                        capturedBitmap,
                        uid,
                        threshold,
                        onResult = { matches ->
                            if (matches.isEmpty()) {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@SearchOrderActivity,
                                        getString(R.string.error_no_image_match).plus(".."),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                return@calculateSimilarity
                            }

                            // En iyi eşleşme (en küçük distance)
                            val best = matches.firstOrNull() ?: run {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@SearchOrderActivity,
                                        getString(R.string.error_no_image_match),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                return@calculateSimilarity
                            }

                            // Snapshot içinden bu imageUrl ile ilişkili siparişi bul
                            var bestPhone: String? = null
                            var bestOrderId: String? = null
                            var bestProductName: String? = null

                            loop@ for (phoneSnap in snapshot.children) {
                                val phoneKey = phoneSnap.key ?: continue
                                for (orderSnap in phoneSnap.children) {
                                    val order = orderSnap.getValue(Order::class.java) ?: continue
                                    if (!order.productImageUrl.isNullOrEmpty() && order.productImageUrl == best.imageUrl) {
                                        bestPhone = phoneKey
                                        bestOrderId = orderSnap.key
                                        bestProductName = order.productName
                                        break@loop
                                    }
                                }
                            }

                            // Görseli indirip dialog ile göster, onaylanırsa ilgili ekrana git
                            if (!best.imageUrl.isNullOrEmpty()) {
                                Thread {
                                    val matchedBitmap = loadBitmapFromUrl(best.imageUrl)
                                    runOnUiThread {
                                        if (matchedBitmap != null) {
                                            val iv = android.widget.ImageView(this@SearchOrderActivity)
                                            iv.setImageBitmap(matchedBitmap)
                                            val dialog = androidx.appcompat.app.AlertDialog.Builder(this@SearchOrderActivity)
                                                .setTitle(getString(R.string.info_image_match_found, (1))) // isteğe bağlı
                                                .setView(iv)
                                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                                    // Eğer sipariş bulunduysa direkt sipariş güncelleme ekranına git
                                                    if (bestPhone != null && bestOrderId != null) {
                                                        val i = Intent(
                                                            this@SearchOrderActivity,
                                                            OrderStatusUpdateActivity::class.java
                                                        )
                                                        i.putExtra(IntentKeys.MERCHANT_UID, uid)
                                                        i.putExtra(IntentKeys.PHONE, bestPhone)
                                                        i.putExtra(IntentKeys.ORDER_ID, bestOrderId)
                                                        i.putExtra(IntentKeys.PRODUCT_NAME, bestProductName)
                                                        startActivity(i)
                                                    } else {
                                                        Toast.makeText(
                                                            this@SearchOrderActivity,
                                                            getString(R.string.info_image_match_found).plus(" (sipariş bulunamadı)"),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                                .setNegativeButton(android.R.string.cancel, null)
                                                .create()
                                            dialog.show()
                                        } else {
                                            Toast.makeText(
                                                this@SearchOrderActivity,
                                                getString(R.string.error_no_records_found),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }.start()
                            } else {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@SearchOrderActivity,
                                        getString(R.string.error_no_image_match),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onError = { e ->
                            runOnUiThread {
                                Toast.makeText(
                                    this@SearchOrderActivity,
                                    getString(R.string.error_generic) + ": " + e.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
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
