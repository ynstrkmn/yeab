package com.yeab.esnapp.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.yeab.esnapp.databinding.ActivitySplashBinding
import com.yeab.esnapp.ui.auth.MerchantLoginActivity
import com.yeab.esnapp.ui.home.HomeActivity
import com.yeab.esnapp.util.IntentKeys

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1.5 sn sonra yönlendirme
        Handler(Looper.getMainLooper()).postDelayed({
            navigateNext()
        }, SPLASH_DELAY_MS)
    }

    private fun navigateNext() {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            // Kullanıcı login, direkt ana sayfaya
            val intent = Intent(this, HomeActivity::class.java)
            intent.putExtra(IntentKeys.MERCHANT_UID, currentUser.uid)
            startActivity(intent)
        } else {
            // Login ekranına git
            val intent = Intent(this, MerchantLoginActivity::class.java)
            startActivity(intent)
        }

        finish()
    }

    companion object {
        private const val SPLASH_DELAY_MS = 1500L
    }
}
