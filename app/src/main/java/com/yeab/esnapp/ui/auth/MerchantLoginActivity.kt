package com.yeab.esnapp.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yeab.esnapp.databinding.ActivityMerchantLoginBinding
import com.yeab.esnapp.util.IntentKeys

class MerchantLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMerchantLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMerchantLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCreateAccount.setOnClickListener {
            val intent = Intent(this, AuthChoiceActivity::class.java)
            intent.putExtra(IntentKeys.MODE, "register")
            startActivity(intent)
        }

        binding.btnLogin.setOnClickListener {
            val intent = Intent(this, AuthChoiceActivity::class.java)
            intent.putExtra(IntentKeys.MODE, "login")
            startActivity(intent)
        }
    }
}
