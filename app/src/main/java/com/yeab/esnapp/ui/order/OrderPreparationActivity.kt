package com.yeab.esnapp.ui.order

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.google.firebase.database.*
import com.yeab.esnapp.databinding.ActivityOrderPreparationBinding
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.util.IntentKeys
import com.yeab.esnapp.util.FirebasePaths
import kotlin.toString

class OrderPreparationActivity : BaseActivity() {

    private lateinit var binding: ActivityOrderPreparationBinding
    private val dbRef = FirebaseDatabase.getInstance().reference
    private var merchantUid: String? = null
    private var currentOrderNumber: String = "-"

    // 1. ADIM: Kameradan (AutoCaptureActivity) gelecek sonucu bekleyen yapı
    private val autoCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Kamera fotoğrafı çekti ve bize URI'yi geri gönderdi
                val capturedImageUri = result.data?.getStringExtra("captured_image_uri")
                val recognizedText = result.data?.getStringExtra(IntentKeys.RECOGNIZED_TEXT)

                if (capturedImageUri != null) {
                    val extraNote = binding.etExtraNote.text?.toString().orEmpty()
                    // Biz de bu sonucu alıp bizi çağıran NewOrderActivity'ye iletiyoruz
                    val data = Intent().apply {
                        putExtra(IntentKeys.ORDER_NUMBER, currentOrderNumber)
                        putExtra("captured_image_uri", capturedImageUri)
                        putExtra("extra_note", extraNote)
                        putExtra(IntentKeys.RECOGNIZED_TEXT, recognizedText)
                    }
                    setResult(RESULT_OK, data)
                    finish() // Kendimizi kapatıyoruz, NewOrderActivity'ye dönüyoruz
                } else {
                    Toast.makeText(this, "Fotoğraf verisi alınamadı", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityOrderPreparationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        // Sipariş numarasını yükle
        loadOrderNumber()

        // 2. ADIM: Butona basınca Kamerayı (AutoCaptureActivity) aç
        binding.btnContinue.setOnClickListener {
            if (currentOrderNumber == "-" || currentOrderNumber.isEmpty()) {
                Toast.makeText(this, "Sipariş numarası yüklenemedi, lütfen bekleyin.", Toast.LENGTH_SHORT).show()
            } else {

                val extraNote = binding.etExtraNote.text?.toString().orEmpty()
                // HATA DÜZELTİLDİ: Artık SearchByOrderIdActivity'ye değil, AutoCaptureActivity'ye gidiyor.
                val intent = Intent(this, AutoCaptureActivity::class.java)
                intent.putExtra(IntentKeys.ORDER_NUMBER, currentOrderNumber)
                intent.putExtra(IntentKeys.RECOGNIZED_TEXT, extraNote)

                // Sonuç bekleyerek başlatıyoruz
                autoCaptureLauncher.launch(intent)
            }
        }
    }

    private fun loadOrderNumber() {
        val uid = merchantUid ?: return
        showLoading()
        dbRef.child(FirebasePaths.MERCHANTS)
            .child(uid)
            .child("OrderNumber")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    hideLoading()
                    val value = snapshot.value
                    // Firebase'den gelen değerin tipini güvenli bir şekilde String'e çeviriyoruz
                    val orderNumber = when (value) {
                        is String -> value
                        is Number -> value.toLong().toString()
                        else -> "-"
                    }



                    currentOrderNumber = orderNumber
                    binding.tvOrderNumber.text = orderNumber
                }

                override fun onCancelled(error: DatabaseError) {
                    hideLoading()
                    currentOrderNumber = "0"
                    binding.tvOrderNumber.text = "-"
                    Toast.makeText(this@OrderPreparationActivity, "Hata: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}