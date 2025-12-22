package com.yeab.esnapp.ui.order

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityOrdersBinding
import com.yeab.esnapp.model.MerchantUser
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.yeab.esnapp.ui.order.MerchantOrdersActivity
import java.io.File
import java.math.BigInteger
import java.util.UUID
import kotlin.toString

class OrdersActivity : BaseActivity() {
    private lateinit var binding: ActivityOrdersBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnActiveOrders.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val intent = Intent(this, MerchantOrdersActivity::class.java)
            intent.putExtra("merchantUid", uid)
            startActivity(intent)
        }

        binding.btnPastOrders.setOnClickListener {
            startActivity(
                android.content.Intent(this, PastOrdersFilterActivity::class.java)
            )
        }
    }
}
