package com.yeab.esnapp.ui.profile

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.yeab.esnapp.databinding.ActivityMerchantProfileBinding
import com.yeab.esnapp.model.Merchant
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys
import com.yeab.esnapp.util.MerchantSession

class MerchantProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMerchantProfileBinding
    private var merchantUid: String? = null

    // Orijinal değerleri değişiklik kıyası için tut
    private var origName: String? = null
    private var origSurname: String? = null
    private var origMerchantName: String? = null
    private var origMerchantType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMerchantProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)
            ?: MerchantSession.merchantUid
                    ?: FirebaseAuth.getInstance().currentUser?.uid

        val uid = merchantUid
        if (uid.isNullOrEmpty()) {
            Toast.makeText(this, "Kullanıcı bulunamadı.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadProfile(uid)

        binding.btnUpdate.setOnClickListener {
            updateProfile(uid)
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun loadProfile(uid: String) {
        val dbRef = FirebaseDatabase.getInstance().reference
        dbRef.child(FirebasePaths.MERCHANTS).child(uid).get()
            .addOnSuccessListener { snap ->
                val merchant = snap.getValue(Merchant::class.java)
                origName = merchant?.Name ?: ""
                origSurname = merchant?.Surname ?: ""
                // Bu alan adları projedeki Merchant şemasına göre varsayılmıştır:
                origMerchantName = merchant?.MerchantName ?: ""
                origMerchantType = merchant?.MerchantType ?: ""

                binding.edtName.setText(origName)
                binding.edtSurname.setText(origSurname)
                binding.edtMerchantName.setText(origMerchantName)
                binding.edtMerchantType.setText(origMerchantType)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Veri alınamadı: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateProfile(uid: String) {
        val name = binding.edtName.text?.toString()?.trim() ?: ""
        val surname = binding.edtSurname.text?.toString()?.trim() ?: ""
        val merchantName = binding.edtMerchantName.text?.toString()?.trim() ?: ""
        val merchantType = binding.edtMerchantType.text?.toString()?.trim() ?: ""

        val updates = hashMapOf<String, Any?>()

        if (name != origName) updates["Name"] = name
        if (surname != origSurname) updates["Surname"] = surname
        if (merchantName != origMerchantName) updates["MerchantName"] = merchantName
        if (merchantType != origMerchantType) updates["MerchantType"] = merchantType

        if (updates.isEmpty()) {
            Toast.makeText(this, "Güncellenecek değişiklik yok.", Toast.LENGTH_SHORT).show()
            return
        }

        FirebaseDatabase.getInstance().reference
            .child(FirebasePaths.MERCHANTS)
            .child(uid)
            .updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profil güncellendi.", Toast.LENGTH_SHORT).show()
                // Yeni değerleri orijinal olarak set et
                origName = name
                origSurname = surname
                origMerchantName = merchantName
                origMerchantType = merchantType
            }
            .addOnFailureListener {
                Toast.makeText(this, "Güncelleme başarısız: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
