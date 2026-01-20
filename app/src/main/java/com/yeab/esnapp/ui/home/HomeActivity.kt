package com.yeab.esnapp.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityHomeBinding
import com.yeab.esnapp.model.Merchant
import com.yeab.esnapp.ui.auth.MerchantLoginActivity
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.ui.messages.MerchantMessageTemplatesActivity
import com.yeab.esnapp.ui.order.NewOrderActivity
import com.yeab.esnapp.ui.order.OrdersActivity
import com.yeab.esnapp.ui.order.SearchOrderActivity
import com.yeab.esnapp.ui.order.UserManualActivity
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys
import com.yeab.esnapp.util.MerchantSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : BaseActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var merchantUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // sesion güvenliği için null yapıldı
        MerchantSession.clear();

        // Login sonrası buraya MERCHANT_UID gönderiyorduk
        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        // Eğer Intent ile gelmediyse, Session'dan almaya çalışalım
        if (merchantUid == null) {
            merchantUid = MerchantSession.merchantUid
        }

        // Eğer hala null ise ve currentUser varsa, currentUser.uid kullanalım
        if (merchantUid == null) {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                merchantUid = currentUser.uid
            }
        }
        
        // Session'a UID'yi kaydedelim
        MerchantSession.merchantUid = merchantUid

        // Veritabanından Merchant bilgisini çek ve Session'a kaydet
        fetchMerchantData()

        // Saat Bilgisi Alınması
        onResume()

        // Yeni Ürün Ekle
        binding.btnNewOrder.setOnClickListener {
            val i = Intent(this, NewOrderActivity::class.java)
            i.putExtra(IntentKeys.MERCHANT_UID, merchantUid)
            startActivity(i)
        }

        // Ürün Listele
        binding.btnListOrders.setOnClickListener {
            val i = Intent(this, SearchOrderActivity::class.java)
            i.putExtra(IntentKeys.MERCHANT_UID, merchantUid)
            startActivity(i)
        }

        binding.btnMessageTemplates.setOnClickListener {
            val i = Intent(this, MerchantMessageTemplatesActivity::class.java)
            i.putExtra(IntentKeys.MERCHANT_UID, merchantUid)
            startActivity(i)
        }

        binding.btnMyOrders.setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
        }

        binding.btnInfo.setOnClickListener {
            startActivity(Intent(this, UserManualActivity::class.java))
        }

        binding.btnProfile.setOnClickListener {
            val i = Intent(this, com.yeab.esnapp.ui.profile.MerchantProfileActivity::class.java)
            i.putExtra(com.yeab.esnapp.util.IntentKeys.MERCHANT_UID, merchantUid)
            startActivity(i)
        }

        // Çıkış Yap
        binding.btnLogout.setOnClickListener {
            showLogoutConfirmDialog()
        }
    }

    // Uygulamaya geri dönüldüğünde saati tekrar güncelle
    override fun onResume() {
        super.onResume()
        updateSystemTime()
    }

    // --- YENİ EKLENEN FONKSİYON ---
    private fun updateSystemTime() {
        try {
            // Şu anki zamanı al: Örnek format "14:30"
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val currentTime = sdf.format(Date())

            // XML'deki TextView'e yaz (ID'sinin txtSystemInfo olduğunu varsayıyoruz)
            // Eğer XML'de ID vermediysen hata verir, XML adımını yapmayı unutma.
            binding.txtSystemInfo.text = "Sistem aktif ve güncel: $currentTime"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun fetchMerchantData() {
        val uid = merchantUid
        if (uid.isNullOrEmpty()) return

        val dbRef = FirebaseDatabase.getInstance().reference
        dbRef.child(FirebasePaths.MERCHANTS).child(uid).addValueEventListener(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val merchant = snapshot.getValue(Merchant::class.java)
                    if (merchant != null) {
                        // Global Session'a kaydet
                        MerchantSession.merchant = merchant

                        // UI güncelle
                        val welcomeText = getString(
                            R.string.welcome_message,
                            "${merchant.Name}",
                            "${merchant.Surname}"
                        )
                        binding.txtWelcomeMessage.text = welcomeText
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HomeActivity, "Merchant verisi alınamadı: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showLogoutConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setPositiveButton(R.string.logout_yes) { _, _ ->
                performLogout()
            }
            .setNegativeButton(R.string.logout_no, null)
            .show()
    }

    private fun performLogout() {
        // Firebase Auth oturumunu kapat
        FirebaseAuth.getInstance().signOut()

        // Session temizle
        MerchantSession.clear()

        // Login ekranına yönlendir
        val intent = Intent(this, MerchantLoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        // Bu activity'yi kapat
        finish()
    }
}
