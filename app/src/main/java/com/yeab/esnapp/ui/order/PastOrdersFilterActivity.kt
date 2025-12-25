package com.yeab.esnapp.ui.order

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.auth.FirebaseAuth
import com.yeab.esnapp.databinding.ActivityPastOrdersFilterBinding
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.ui.components.PhoneContactInput

class PastOrdersFilterActivity : BaseActivity() {

    private lateinit var binding: ActivityPastOrdersFilterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPastOrdersFilterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup PhoneContactInput
        val contactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    binding.phoneInputComponent.setPhoneNumberFromUri(uri)
                }
            }
        }

        binding.phoneInputComponent.onPickContactClick = {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactLauncher.launch(intent)
        }

        val ranges = listOf("Seçiniz", "1 gün", "1 hafta", "1 ay", "3 ay", "1 yıl")
        binding.spnDateRange.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            ranges
        )

        binding.spnDateRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                // Not: TextWatcher bileşenin iç yapısında kaldığı için dışarıdan doğrudan erişilemez,
                // ancak mantığı korumak adına telefon doluysa burayı sıfırlama mantığını 
                // arama butonuna tıkladığımızda kontrol ediyoruz.
                // Eğer anlık silme isteniyorsa PhoneContactInput'a text watcher ekleme yeteneği kazandırılmalı.
                // Şimdilik mevcut yapıyı koruyarak devam ediyoruz.
                if (position > 0 && binding.phoneInputComponent.getPhoneNumber().isNotEmpty()) {
                    binding.phoneInputComponent.setPhoneNumber("")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // PhoneContactInput içindeki EditText'e erişimimiz kısıtlı olduğu için
        // TextWatcher mantığını buraya taşımak yerine, bileşenin mantığını sadeleştirip
        // arama sırasında kontrol yapıyoruz.
        // Eğer kullanıcı telefon girerse spinner'ı sıfırlama işini burada yapamıyoruz (TextWatcher yok).
        // Ancak kullanıcı deneyimi açısından çok kritik değilse arama butonunda kontrol yeterli.
        
        // onCreate içinde:
        binding.btnSearch.setOnClickListener {
            val phone = binding.phoneInputComponent.getPhoneNumber()
            val selectedIndex = binding.spnDateRange.selectedItemPosition
            val usePhone = phone.isNotEmpty()
            val useDateRange = selectedIndex > 0

            if (usePhone == useDateRange) {
                // İkisi de seçili veya ikisi de boş ise
                if (usePhone) {
                     // İkisi de doluysa telefon öncelikli olsun veya kullanıcı uyarısın
                     // Eski kod mantığı: "biri ile arama yapabilirsiniz"
                     Toast.makeText(this, "Sadece telefon numarası ya da tarih aralığı ile arama yapabilirsiniz", Toast.LENGTH_SHORT).show()
                     return@setOnClickListener
                } else {
                     // İkisi de boş
                     Toast.makeText(this, "Lütfen bir arama kriteri seçiniz", Toast.LENGTH_SHORT).show()
                     return@setOnClickListener
                }
            }

            val merchantUid = FirebaseAuth.getInstance().currentUser?.uid

            if (usePhone) {
                val intent = Intent(this, SearchResultsActivity::class.java).apply {
                    putExtra("merchantUid", merchantUid)
                    putExtra("phone", phone)
                }
                startActivity(intent)
            } else {
                // Tarih aralığı - bugün referans alınır
                val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                val cal = java.util.Calendar.getInstance() // bugün
                val endDate = sdf.format(cal.time)

                when (selectedIndex) {
                    1 -> cal.add(java.util.Calendar.DAY_OF_YEAR, -1)   // 1 gün
                    2 -> cal.add(java.util.Calendar.WEEK_OF_YEAR, -1)  // 1 hafta
                    3 -> cal.add(java.util.Calendar.MONTH, -1)         // 1 ay
                    4 -> cal.add(java.util.Calendar.MONTH, -3)         // 3 ay
                    5 -> cal.add(java.util.Calendar.YEAR, -1)          // 1 yıl
                    else -> {
                        Toast.makeText(this, "Geçerli bir tarih aralığı seçiniz", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
                val startDate = sdf.format(cal.time)

                val intent = Intent(this, SearchResultsActivity::class.java).apply {
                    putExtra("merchantUid", merchantUid)
                    putExtra("startDate", startDate)
                    putExtra("endDate", endDate)
                }
                startActivity(intent)
            }
        }

    }
}
