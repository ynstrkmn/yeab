package com.yeab.esnapp.ui.order

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityPastOrdersFilterBinding
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.ui.components.PhoneContactInput

class PastOrdersFilterActivity : BaseActivity() {

    private lateinit var binding: ActivityPastOrdersFilterBinding
    private lateinit var contactLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestContactPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityPastOrdersFilterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Rehber seçici launcher (sınıf alanına atanıyor)
        contactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    binding.phoneInputComponent.setPhoneNumberFromUri(uri)
                }
            }
        }

        // İzin isteği launcher
        requestContactPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pickContact()
            } else {
                Toast.makeText(this, getString(R.string.error_permission_required), Toast.LENGTH_SHORT).show()
            }
        }

        // Rehber butonuna tıklama olayı (izin kontrolü ile)
        binding.phoneInputComponent.onPickContactClick = {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                pickContact()
            } else {
                requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
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
                if (position > 0 && binding.phoneInputComponent.getPhoneNumber().isNotEmpty()) {
                    binding.phoneInputComponent.setPhoneNumber("")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnSearch.setOnClickListener {
            val phone = binding.phoneInputComponent.getPhoneNumber()
            val selectedIndex = binding.spnDateRange.selectedItemPosition
            val usePhone = phone.isNotEmpty()
            val useDateRange = selectedIndex > 0

            if (usePhone == useDateRange) {
                if (usePhone) {
                    Toast.makeText(this, "Sadece telefon numarası ya da tarih aralığı ile arama yapabilirsiniz", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                } else {
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
                val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                val cal = java.util.Calendar.getInstance()
                val endDate = sdf.format(cal.time)

                when (selectedIndex) {
                    1 -> cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                    2 -> cal.add(java.util.Calendar.WEEK_OF_YEAR, -1)
                    3 -> cal.add(java.util.Calendar.MONTH, -1)
                    4 -> cal.add(java.util.Calendar.MONTH, -3)
                    5 -> cal.add(java.util.Calendar.YEAR, -1)
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

    private fun pickContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactLauncher.launch(intent)
    }
}
