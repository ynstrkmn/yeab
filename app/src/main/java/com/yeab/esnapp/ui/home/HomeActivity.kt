package com.yeab.esnapp.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityHomeBinding
import com.yeab.esnapp.ui.auth.MerchantLoginActivity   // <-- login ekranının gerçek paketini burada düzelt
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.ui.messages.MerchantMessageTemplatesActivity
import com.yeab.esnapp.ui.order.NewOrderActivity
import com.yeab.esnapp.ui.order.SearchOrderActivity
import com.yeab.esnapp.util.IntentKeys
import com.yeab.esnapp.ui.order.OrdersActivity

class HomeActivity : BaseActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var merchantUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Login sonrası buraya MERCHANT_UID gönderiyorduk
        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        val currentUser = FirebaseAuth.getInstance().currentUser
        binding.txtWelcomeMessage.text = "${binding.txtWelcomeMessage.text} ${currentUser?.displayName}"

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

        // binding zaten setup edildiğini varsayıyorum. onCreate içinde uygun yere ekleyin:
        binding.btnMyOrders.setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
        }


        // Çıkış Yap
        binding.btnLogout.setOnClickListener {
            showLogoutConfirmDialog()
        }
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

        // Eğer SessionManager kullanıyorsan, merchant bilgilerini de temizle
        try {
            //SessionManager.clear(this)
        } catch (_: Exception) {
            // SessionManager yoksa bu kısmı tamamen silebilirsin
        }

        // Login ekranına yönlendir
        val intent = Intent(this, MerchantLoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        // Bu activity'yi kapat
        finish()
    }
}
