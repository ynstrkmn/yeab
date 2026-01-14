package com.yeab.esnapp.ui.order

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.firebase.database.*
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivitySearchByOrderIdBinding
import com.yeab.esnapp.model.Order
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.util.FirebasePaths
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
                        val intent = Intent(this@SearchByOrderIdActivity, OrderStatusUpdateActivity::class.java)
                        intent.putExtra(IntentKeys.MERCHANT_UID, uid)
                        intent.putExtra(IntentKeys.PHONE, foundPhone)
                        intent.putExtra(IntentKeys.ORDER_ID, foundTimestampKey) // We still send the Firebase Key as ORDER_ID to the next activity
                        intent.putExtra(IntentKeys.PRODUCT_NAME, foundOrder.productName)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@SearchByOrderIdActivity, getString(R.string.error_no_records_found), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    hideLoading()
                    Toast.makeText(this@SearchByOrderIdActivity, error.message, Toast.LENGTH_SHORT).show()
                }
            })
    }
}
