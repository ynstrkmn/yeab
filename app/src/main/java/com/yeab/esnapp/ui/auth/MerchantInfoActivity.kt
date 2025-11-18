package com.yeab.esnapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityMerchantInfoBinding
import com.yeab.esnapp.model.Merchant
import com.yeab.esnapp.ui.home.HomeActivity
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys

class MerchantInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMerchantInfoBinding
    private val dbRef = FirebaseDatabase.getInstance().reference
    private var merchantUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMerchantInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        binding.btnSave.setOnClickListener {
            saveMerchant()
        }
    }

    private fun saveMerchant() {
        val birthDate = binding.edtBirthDate.text.toString().trim()
        val city = binding.edtCity.text.toString().trim()
        val district = binding.edtDistrict.text.toString().trim()
        val coords = binding.edtCoordinates.text.toString().trim()
        val merchantName = binding.edtMerchantName.text.toString().trim()
        val merchantType = binding.edtMerchantType.text.toString().trim()
        val mobile = binding.edtMobile.text.toString().trim()
        val name = binding.edtName.text.toString().trim()
        val surname = binding.edtSurname.text.toString().trim()

        if (birthDate.isEmpty() || city.isEmpty() || district.isEmpty() ||
            coords.isEmpty() || merchantName.isEmpty() || merchantType.isEmpty() ||
            mobile.isEmpty() || name.isEmpty() || surname.isEmpty()
        ) {
            Toast.makeText(this, getString(R.string.error_fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        val uid = merchantUid ?: return

        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                val token = if (task.isSuccessful) task.result else null

                val merchant = Merchant(
                    birthDate,
                    city,
                    coords,
                    district,
                    merchantName,
                    merchantType,
                    mobile.toLong(),
                    name,
                    surname,
                    token
                )

                dbRef.child(FirebasePaths.MERCHANTS)
                    .child(uid)
                    .setValue(merchant)
                    .addOnSuccessListener {
                        Toast.makeText(this, getString(R.string.info_merchant_saved), Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, HomeActivity::class.java)
                        intent.putExtra(IntentKeys.MERCHANT_UID, uid)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, it.message ?: getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                    }
            }
    }
}
