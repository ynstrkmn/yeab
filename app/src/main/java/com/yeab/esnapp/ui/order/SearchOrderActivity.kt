package com.yeab.esnapp.ui.order

import ImageMatchAdapter
import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.database.*
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivitySearchOrderBinding
import com.yeab.esnapp.model.Order
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.ImageSimilarityUtils
import com.yeab.esnapp.util.IntentKeys
import java.io.File

class SearchOrderActivity : BaseActivity() {

    private lateinit var binding: ActivitySearchOrderBinding
    private var merchantUid: String? = null
    private val dbRef = FirebaseDatabase.getInstance().reference

    private var photoUri: Uri? = null

    // Kamera sonucu
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = photoUri
                if (uri == null) {
                    Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT)
                        .show()
                    return@registerForActivityResult
                }

                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            searchByImage(bitmap)
                        } else {
                            Toast.makeText(
                                this,
                                getString(R.string.error_generic),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        getString(R.string.error_generic) + ": " + e.message,
                        Toast.LENGTH_SHORT
                    ).show()
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySearchOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        binding.btnSearchByImage.setOnClickListener {
            checkCameraPermissionAndOpen()
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
        val photoFile = File(
            cacheDir,
            "search_${System.currentTimeMillis()}.jpg"
        )

        photoUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            photoFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        cameraLauncher.launch(intent)
    }

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
                                    val url = order.productImageUrl
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

    private fun showImageDialog(imageUrl: String) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_image_preview)
        val imageView = dialog.findViewById<ImageView>(R.id.imgPreview)
        Glide.with(this)
            .load(imageUrl)
            .fitCenter()
            .apply( RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH))
            .into(imageView)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }
}
