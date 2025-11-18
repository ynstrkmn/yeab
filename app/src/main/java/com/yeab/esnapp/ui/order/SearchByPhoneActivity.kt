package com.yeab.esnapp.ui.order

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivitySearchByPhoneBinding
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys

class SearchByPhoneActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchByPhoneBinding
    private val dbRef = FirebaseDatabase.getInstance().reference
    private var merchantUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchByPhoneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        binding.btnSearch.setOnClickListener {
            searchOrders()
        }
    }

    private fun searchOrders() {
        val phone = binding.edtPhone.text.toString().trim()
        val uid = merchantUid ?: return

        if (phone.length != 10) {
            Toast.makeText(this, getString(R.string.error_phone_10_digits), Toast.LENGTH_SHORT).show()
            return
        }

        dbRef.child(FirebasePaths.ORDERS_ROOT)
            .child(FirebasePaths.ORDERS_MERCHANT_ORDERS)
            .child(uid)
            .child(phone)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        Toast.makeText(this@SearchByPhoneActivity, getString(R.string.error_no_records_found), Toast.LENGTH_SHORT).show()
                        return
                    }

                    val i = Intent(this@SearchByPhoneActivity, OrderListActivity::class.java)
                    i.putExtra(IntentKeys.MERCHANT_UID, uid)
                    i.putExtra(IntentKeys.PHONE, phone)
                    startActivity(i)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
