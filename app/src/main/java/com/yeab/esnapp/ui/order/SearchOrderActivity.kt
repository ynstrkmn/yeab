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
                    // Hamming threshold (ImageSimilarityUtils içinde kullanılıyor)
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

                            // 1) En iyi 3 eşleşmeyi al (distance küçükten büyüğe sıralanmış geliyor)
                            val topMatches = matches.take(3)

                            // 2) Snapshot'ı tek seferde dolaşıp imageUrl -> (phone, orderId, productName) map'i oluştur
                            val urlToOrderInfo = mutableMapOf<String, Triple<String, String, String?>>()

                            for (phoneSnap in snapshot.children) {
                                val phoneKey = phoneSnap.key ?: continue
                                for (orderSnap in phoneSnap.children) {
                                    val order = orderSnap.getValue(Order::class.java) ?: continue
                                    val url = order.productImageUrl
                                    val orderId = orderSnap.key ?: continue

                                    if (!url.isNullOrEmpty() && !urlToOrderInfo.containsKey(url)) {
                                        urlToOrderInfo[url] =
                                            Triple(phoneKey, orderId, order.productName)
                                    }
                                }
                            }

                            // 3) En iyi 3 eşleşmeden gerçekten siparişle eşleşenleri topla
                            data class MatchedOrderUi(
                                val match: com.yeab.esnapp.util.MatchResult,
                                val phone: String,
                                val orderId: String,
                                val productName: String?
                            )

                            val matchedOrders = mutableListOf<MatchedOrderUi>()

                            for (match in topMatches) {
                                val url = match.imageUrl
                                if (!url.isNullOrEmpty()) {
                                    val info = urlToOrderInfo[url]
                                    if (info != null) {
                                        val (phone, orderId, productName) = info
                                        matchedOrders.add(
                                            MatchedOrderUi(
                                                match = match,
                                                phone = phone,
                                                orderId = orderId,
                                                productName = productName
                                            )
                                        )
                                    }
                                }
                            }

                            if (matchedOrders.isEmpty()) {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@SearchOrderActivity,
                                        getString(R.string.error_no_image_match),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                return@calculateSimilarity
                            }

                            // 4) Kullanıcıya gösterilecek liste item text'lerini hazırla
                            //    Format: "85% - Pantolon A1 (5311000001)"
                            val items = matchedOrders.map { m ->
                                val pct = m.match.percentage.coerceAtLeast(0)
                                val name = m.productName ?: "-"
                                "${pct}% - $name (${m.phone})"
                            }.toTypedArray()

                            runOnUiThread {
                                // 5) Dialog ile kullanıcıya 3'lüyü sun, seçtiğini OrderStatusUpdateActivity'ye taşı
                                val dialog = androidx.appcompat.app.AlertDialog.Builder(this@SearchOrderActivity)
                                    .setTitle("")
                                    .setItems(items) { _, which ->
                                        val selected = matchedOrders[which]
                                        val i = Intent(
                                            this@SearchOrderActivity,
                                            OrderStatusUpdateActivity::class.java
                                        )
                                        i.putExtra(IntentKeys.MERCHANT_UID, uid)
                                        i.putExtra(IntentKeys.PHONE, selected.phone)
                                        i.putExtra(IntentKeys.ORDER_ID, selected.orderId)
                                        i.putExtra(IntentKeys.PRODUCT_NAME, selected.productName)
                                        startActivity(i)
                                    }
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .create()

                                dialog.show()
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
