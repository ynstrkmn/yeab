package com.yeab.esnapp.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yeab.esnapp.databinding.ActivityHomeBinding
import com.yeab.esnapp.ui.order.NewOrderActivity
import com.yeab.esnapp.ui.order.SearchOrderActivity
import com.yeab.esnapp.util.IntentKeys

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var merchantUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        binding.btnNewOrder.setOnClickListener {
            val intent = Intent(this, NewOrderActivity::class.java)
            intent.putExtra(IntentKeys.MERCHANT_UID, merchantUid)
            startActivity(intent)
        }

        binding.btnListOrders.setOnClickListener {
            val intent = Intent(this, SearchOrderActivity::class.java)
            intent.putExtra(IntentKeys.MERCHANT_UID, merchantUid)
            startActivity(intent)
        }
    }
}
