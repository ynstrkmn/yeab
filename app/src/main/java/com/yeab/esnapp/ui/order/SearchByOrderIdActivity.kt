// app/src/main/java/com/yeab/esnapp/ui/order/SearchByOrderIdActivity.kt
package com.yeab.esnapp.ui.order

import ImageMatchAdapter
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivitySearchByOrderIdBinding
import com.yeab.esnapp.model.Order
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.ImageSimilarityUtils
import com.yeab.esnapp.util.IntentKeys

class SearchByOrderIdActivity : BaseActivity() {

    private lateinit var binding: ActivitySearchByOrderIdBinding
    private val dbRef = FirebaseDatabase.getInstance().reference
    private var merchantUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchByOrderIdBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        binding.btnSearch.setOnClickListener {
            searchByOrderNumber()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun searchByOrderNumber() {
        val inputOrderNumber = binding.etOrderNumber.text.toString().trim()
        val uid = merchantUid ?: return

        if (inputOrderNumber.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        showLoading()

        dbRef.child(FirebasePaths.ORDERS_ROOT)
            .child(FirebasePaths.ORDERS_MERCHANT_ORDERS)
            .child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var foundOrder: Order? = null
                    var foundPhone: String? = null
                    var foundTimestampKey: String? = null

                    for (phoneSnap in snapshot.children) {
                        val phoneKey = phoneSnap.key ?: continue
                        for (orderSnap in phoneSnap.children) {
                            val order = orderSnap.getValue(Order::class.java)
                            if (order?.orderNumber == inputOrderNumber) {
                                foundOrder = order
                                foundPhone = phoneKey
                                foundTimestampKey = orderSnap.key
                                break
                            }
                        }
                        if (foundOrder != null) break
                    }

                    hideLoading()

                    if (foundOrder != null && foundPhone != null && foundTimestampKey != null) {
                        navigateToOrderStatus(uid, foundPhone!!, foundTimestampKey!!, foundOrder!!.productName)
                    } else {
                        // Ana arama başarısızsa metin tabanlı yedeği çağır
                        searchByImageText(inputOrderNumber)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    hideLoading()
                    Toast.makeText(this@SearchByOrderIdActivity, error.message, Toast.LENGTH_SHORT).show()
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
                                    showNoMatchDialog()
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
                                        this@SearchByOrderIdActivity,
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
                                    androidx.recyclerview.widget.LinearLayoutManager(this@SearchByOrderIdActivity)

                                // Dialog referansı, tıklamada kapatmak için
                                var alertDialog: androidx.appcompat.app.AlertDialog? = null

                                val adapter = ImageMatchAdapter(
                                    matchedOrders,
                                    onClick = { selected ->
                                        val intent = Intent(
                                            this@SearchByOrderIdActivity,
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
                                    androidx.appcompat.app.AlertDialog.Builder(this@SearchByOrderIdActivity)
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
                                    this@SearchByOrderIdActivity,
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
                            this@SearchByOrderIdActivity,
                            error.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })

    }

    private fun showNoMatchDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_no_match_title))
            .setMessage("Bu bilgiler ile herhangi bir ürün bulamadım. İsterseniz işlemde olan ürünlerim sayfasına yönlendirebilirim.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                navigateToProcessingProducts()
            }
            .show()
    }

    private fun navigateToProcessingProducts() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val intent = Intent(this, MerchantOrdersActivity::class.java)
        intent.putExtra("merchantUid", uid)
        startActivity(intent)
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



    private fun navigateToOrderStatus(uid: String, phone: String, orderId: String, productName: String) {
        val intent = Intent(this, OrderStatusUpdateActivity::class.java).apply {
            putExtra(IntentKeys.MERCHANT_UID, uid)
            putExtra(IntentKeys.PHONE, phone)
            putExtra(IntentKeys.ORDER_ID, orderId)
            putExtra(IntentKeys.PRODUCT_NAME, productName)
        }
        startActivity(intent)
        finish()
    }

    private data class SearchResult(
        val phone: String,
        val timestampKey: String,
        val productName: String
    )
}
