// kotlin
package com.yeab.esnapp.ui.order

import android.content.Intent
import android.os.Bundle
import androidx.core.view.WindowCompat
import com.google.firebase.database.*
import com.yeab.esnapp.databinding.ActivityOrderPreparationBinding
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.util.IntentKeys
import com.yeab.esnapp.util.FirebasePaths

class OrderPreparationActivity : BaseActivity() {

    private lateinit var binding: ActivityOrderPreparationBinding
    private val dbRef = FirebaseDatabase.getInstance().reference
    private var merchantUid: String? = null
    private var currentOrderNumber: String = "-"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityOrderPreparationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        loadOrderNumber()

        binding.btnContinue.setOnClickListener {
            val data = Intent().apply {
                putExtra(IntentKeys.ORDER_NUMBER, currentOrderNumber)
            }
            setResult(RESULT_OK, data)
            finish()
        }
    }

    private fun loadOrderNumber() {
        val uid = merchantUid ?: return
        showLoading()
        dbRef.child(FirebasePaths.MERCHANTS)
            .child(uid)
            .child("OrderNumber")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    hideLoading()
                    val value = snapshot.value
                    val orderNumber = when (value) {
                        is String -> value
                        is Number -> value.toLong().toString()
                        else -> "-"
                    }
                    currentOrderNumber = orderNumber
                    binding.tvOrderNumber.text = orderNumber
                }
                override fun onCancelled(error: DatabaseError) {
                    hideLoading()
                    currentOrderNumber = "-"
                    binding.tvOrderNumber.text = "-"
                }
            })
    }
}
