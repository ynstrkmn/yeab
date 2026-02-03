package com.yeab.esnapp.ui.order

import ImageMatchAdapter
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import com.google.firebase.database.*
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivitySearchOrderBinding
import com.yeab.esnapp.model.Order
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.ImageSimilarityUtils
import com.yeab.esnapp.util.IntentKeys

class SearchOrderActivity : BaseActivity() {

    private lateinit var binding: ActivitySearchOrderBinding
    private var merchantUid: String? = null
    private val dbRef = FirebaseDatabase.getInstance().reference
    val currentOrderNumber = ""

    // DEĞİŞİKLİK 1: Eski "cameraLauncher" yerine bizim özel kamerayı bekleyen launcher
    private val customCameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // SearchCameraActivity'den dönen fotoğrafın yolu
                val capturedUriString = result.data?.getStringExtra("captured_image_uri")

                if (capturedUriString != null) {
                    val uri = Uri.parse(capturedUriString)
                    // Fotoğrafı Bitmap'e çevirip ESKİ MANTIĞA gönderiyoruz
                    processCapturedImage(uri)
                } else {
                    Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val autoCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Kamera fotoğrafı çekti ve bize URI'yi geri gönderdi
                val capturedImageUri = result.data?.getStringExtra("captured_image_uri")
                val recognizedText = result.data?.getStringExtra(IntentKeys.RECOGNIZED_TEXT)

                if (capturedImageUri != null) {
                    // Biz de bu sonucu alıp bizi çağıran NewOrderActivity'ye iletiyoruz
                    val data = Intent().apply {
                        putExtra(IntentKeys.ORDER_NUMBER, currentOrderNumber)
                        putExtra("captured_image_uri", capturedImageUri)
                        putExtra(IntentKeys.RECOGNIZED_TEXT, recognizedText)
                    }
                    searchByImageText(recognizedText ?: "")
                } else {
                    Toast.makeText(this, "Fotoğraf verisi alınamadı", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySearchOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        // DEĞİŞİKLİK 2: Butona basınca System Kamerası değil, bizim Özel Kamera açılıyor
        binding.btnSearchByImage.setOnClickListener {
            val intent = Intent(this, AutoCaptureActivity::class.java)
            intent.putExtra(IntentKeys.ORDER_NUMBER, currentOrderNumber)

            // Sonuç bekleyerek başlatıyoruz
            autoCaptureLauncher.launch(intent)
        }

        binding.btnSearchByOrderId.setOnClickListener {
            val i = Intent(this, SearchByOrderIdActivity::class.java)
            i.putExtra(IntentKeys.MERCHANT_UID, merchantUid)
            startActivity(i)
        }

        binding.btnSearchByPhone.setOnClickListener {
            val i = Intent(this, SearchByPhoneActivity::class.java)
            i.putExtra(IntentKeys.MERCHANT_UID, merchantUid)
            startActivity(i)
        }

        // Back butonu varsa (XML'de ekli görünüyor)
        binding.btnBack.setOnClickListener { finish() }
    }

    // Bizim gölgeli/kareli kamerayı açan fonksiyon
    private fun openCustomCamera() {
        val intent = Intent(this, SearchCameraActivity::class.java)
        customCameraLauncher.launch(intent)
    }

    // Gelen fotoğrafı Bitmap'e çevirip senin eski arama fonksiyonuna veren köprü
    private fun processCapturedImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    // İŞTE BURASI: Senin orijinal kodun çalışıyor
                    searchByImage(bitmap)
                } else {
                    Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_generic) + ": " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    // --- BURADAN AŞAĞISI SENİN ORİJİNAL KODUN (HİÇ DOKUNULMADI) ---

    private fun showNoMatchDialog(uid: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_no_match_title))
            .setMessage(getString(R.string.dialog_no_match_message))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val intent = Intent(this, SearchByOrderIdActivity::class.java)
                intent.putExtra(IntentKeys.MERCHANT_UID, uid)
                startActivity(intent)
            }
            .setCancelable(false)
            .show()
    }

    private fun searchByImage(capturedBitmap: Bitmap) {
        val uid = merchantUid ?: return

        showLoading()

        dbRef.child(FirebasePaths.ORDERS_ROOT)
            .child(FirebasePaths.ORDERS_MERCHANT_ORDERS)
            .child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    val threshold = 40

                    ImageSimilarityUtils.calculateSimilarity(
                        capturedBitmap,
                        uid,
                        threshold,
                        onResult = { matches ->
                            if (matches.isEmpty()) {
                                runOnUiThread {
                                    hideLoading()
                                    Toast.makeText(
                                        this@SearchOrderActivity,
                                        getString(R.string.error_no_image_match).plus(".."),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showNoMatchDialog(uid)
                                }
                                return@calculateSimilarity
                            }

                            val topMatches = matches.take(3)

                            val urlToOrderInfo =
                                mutableMapOf<String, Triple<String, String, String?>>()

                            for (phoneSnap in snapshot.children) {
                                val phoneKey = phoneSnap.key ?: continue
                                for (orderSnap in phoneSnap.children) {
                                    val order = orderSnap.getValue(Order::class.java) ?: continue
                                    // val url = order.productImageUrl
                                    val orderId = orderSnap.key ?: continue

                                    if ( !urlToOrderInfo.containsKey(orderId)) {
                                        urlToOrderInfo[orderId] =
                                            Triple(phoneKey, orderId, order.productName)
                                    }
                                }
                            }

                            val matchedOrders = mutableListOf<ImageMatchAdapter.MatchedOrderUi>()

                            for (match in topMatches) {
                                val orderId = match.imageId
                                if (!orderId.isNullOrEmpty()) {
                                    val info = urlToOrderInfo[orderId]
                                    if (info != null) {
                                        val (phone, orderId, productName) = info
                                        matchedOrders.add(
                                            ImageMatchAdapter.MatchedOrderUi(
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
                                    hideLoading()
                                    Toast.makeText(
                                        this@SearchOrderActivity,
                                        getString(R.string.error_no_image_match),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                return@calculateSimilarity
                            }

                            runOnUiThread {
                                hideLoading()

                                val dialogView = layoutInflater.inflate(
                                    R.layout.dialog_image_matches,
                                    null
                                )
                                val recycler =
                                    dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(
                                        R.id.recyclerImageMatches
                                    )
                                recycler.layoutManager =
                                    androidx.recyclerview.widget.LinearLayoutManager(this@SearchOrderActivity)

                                // Dialog referansı, tıklamada kapatmak için
                                var alertDialog: androidx.appcompat.app.AlertDialog? = null

                                val adapter = ImageMatchAdapter(
                                    matchedOrders,
                                    onClick = { selected ->
                                        val intent = Intent(
                                            this@SearchOrderActivity,
                                            OrderStatusUpdateActivity::class.java
                                        )
                                        intent.putExtra(IntentKeys.MERCHANT_UID, uid)
                                        intent.putExtra(IntentKeys.PHONE, selected.phone)
                                        intent.putExtra(IntentKeys.ORDER_ID, selected.orderId)
                                        intent.putExtra(IntentKeys.PRODUCT_NAME, selected.productName)
                                        startActivity(intent)
                                        alertDialog?.dismiss()
                                    },
                                    onImageClick = { imageUrl ->
                                        showImageDialog(imageUrl)
                                    }
                                )

                                recycler.adapter = adapter

                                alertDialog =
                                    androidx.appcompat.app.AlertDialog.Builder(this@SearchOrderActivity)
                                        .setTitle(getString(R.string.search_results_title))
                                        .setView(dialogView)
                                        .create()

                                alertDialog.show()
                            }
                        },
                        onError = { e ->
                            runOnUiThread {
                                hideLoading()
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
                        hideLoading()
                        Toast.makeText(
                            this@SearchOrderActivity,
                            error.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })

    }

    private fun searchByImageText(recognizedText: String) {
        val uid = merchantUid ?: return

        showLoading()

        dbRef.child(FirebasePaths.ORDERS_ROOT)
            .child(FirebasePaths.ORDERS_MERCHANT_ORDERS)
            .child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    val threshold = 40

                    ImageSimilarityUtils.calculateSimilarityFromText(
                        recognizedText,
                        uid,
                        threshold,
                        onResult = { matches ->
                            if (matches.isEmpty()) {
                                runOnUiThread {
                                    hideLoading()
                                    Toast.makeText(
                                        this@SearchOrderActivity,
                                        getString(R.string.error_no_image_match).plus(".."),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showNoMatchDialog(uid)
                                }
                                return@calculateSimilarityFromText
                            }

                            val topMatches = matches.take(3)

                            val urlToOrderInfo =
                                mutableMapOf<String, Triple<String, String, String?>>()

                            for (phoneSnap in snapshot.children) {
                                val phoneKey = phoneSnap.key ?: continue
                                for (orderSnap in phoneSnap.children) {
                                    val order = orderSnap.getValue(Order::class.java) ?: continue
                                    // val url = order.productImageUrl
                                    val orderId = orderSnap.key ?: continue

                                    if ( !urlToOrderInfo.containsKey(orderId)) {
                                        urlToOrderInfo[orderId] =
                                            Triple(phoneKey, orderId, order.productName)
                                    }
                                }
                            }

                            val matchedOrders = mutableListOf<ImageMatchAdapter.MatchedOrderUi>()

                            for (match in topMatches) {
                                val orderId = match.imageId
                                if (!orderId.isNullOrEmpty()) {
                                    val info = urlToOrderInfo[orderId]
                                    if (info != null) {
                                        val (phone, orderId, productName) = info
                                        matchedOrders.add(
                                            ImageMatchAdapter.MatchedOrderUi(
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
                                    hideLoading()
                                    Toast.makeText(
                                        this@SearchOrderActivity,
                                        getString(R.string.error_no_image_match),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                return@calculateSimilarityFromText
                            }

                            runOnUiThread {
                                hideLoading()

                                val dialogView = layoutInflater.inflate(
                                    R.layout.dialog_image_matches,
                                    null
                                )
                                val recycler =
                                    dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(
                                        R.id.recyclerImageMatches
                                    )
                                recycler.layoutManager =
                                    androidx.recyclerview.widget.LinearLayoutManager(this@SearchOrderActivity)

                                // Dialog referansı, tıklamada kapatmak için
                                var alertDialog: androidx.appcompat.app.AlertDialog? = null

                                val adapter = ImageMatchAdapter(
                                    matchedOrders,
                                    onClick = { selected ->
                                        val intent = Intent(
                                            this@SearchOrderActivity,
                                            OrderStatusUpdateActivity::class.java
                                        )
                                        intent.putExtra(IntentKeys.MERCHANT_UID, uid)
                                        intent.putExtra(IntentKeys.PHONE, selected.phone)
                                        intent.putExtra(IntentKeys.ORDER_ID, selected.orderId)
                                        intent.putExtra(IntentKeys.PRODUCT_NAME, selected.productName)
                                        startActivity(intent)
                                        alertDialog?.dismiss()
                                    },
                                    onImageClick = { imageUrl ->
                                        showImageDialog(imageUrl)
                                    }
                                )

                                recycler.adapter = adapter

                                alertDialog =
                                    androidx.appcompat.app.AlertDialog.Builder(this@SearchOrderActivity)
                                        .setTitle(getString(R.string.search_results_title))
                                        .setView(dialogView)
                                        .create()

                                alertDialog.show()
                            }
                        },
                        onError = { e ->
                            runOnUiThread {
                                hideLoading()
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
                        hideLoading()
                        Toast.makeText(
                            this@SearchOrderActivity,
                            error.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })

    }

    private fun showImageDialog(imageUrl: String) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_image_preview)
        val imageView = dialog.findViewById<ImageView>(R.id.imgPreview)
        Glide.with(this)
            .load(imageUrl)
            .fitCenter()
            .into(imageView)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }
}