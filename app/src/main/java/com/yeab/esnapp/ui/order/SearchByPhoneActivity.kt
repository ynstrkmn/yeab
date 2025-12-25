package com.yeab.esnapp.ui.order

import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.database.*
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivitySearchByPhoneBinding
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys

class SearchByPhoneActivity : BaseActivity() {

    private lateinit var binding: ActivitySearchByPhoneBinding
    private val dbRef = FirebaseDatabase.getInstance().reference
    private var merchantUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchByPhoneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        // Rehber seçici launcher
        val contactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    binding.phoneInputComponent.setPhoneNumberFromUri(uri)
                }
            }
        }

        // Rehber butonuna tıklama olayı
        binding.phoneInputComponent.onPickContactClick = {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactLauncher.launch(intent)
        }

        binding.btnSearch.setOnClickListener {
            searchOrders()
        }
    }

    private fun searchOrders() {
        // Yeni component üzerinden numarayı alıyoruz
        val phone = binding.phoneInputComponent.getPhoneNumber()
        val uid = merchantUid ?: return

        if (phone.length != 10) {
            Toast.makeText(this, getString(R.string.error_phone_10_digits), Toast.LENGTH_SHORT).show()
            return
        }

        showLoading()

        dbRef.child(FirebasePaths.ORDERS_ROOT)
            .child(FirebasePaths.ORDERS_MERCHANT_ORDERS)
            .child(uid)
            .child(phone)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    hideLoading()
                    if (!snapshot.exists()) {
                        Toast.makeText(this@SearchByPhoneActivity, getString(R.string.error_no_records_found), Toast.LENGTH_SHORT).show()
                        return
                    }

                    val i = Intent(this@SearchByPhoneActivity, OrderListActivity::class.java)
                    i.putExtra(IntentKeys.MERCHANT_UID, uid)
                    i.putExtra(IntentKeys.PHONE, phone)
                    startActivity(i)
                }

                override fun onCancelled(error: DatabaseError) {
                    hideLoading()
                }
            })
    }
}