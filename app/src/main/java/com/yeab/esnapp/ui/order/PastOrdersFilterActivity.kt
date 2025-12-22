package com.yeab.esnapp.ui.order

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityPastOrdersFilterBinding
import com.yeab.esnapp.ui.base.BaseActivity
import kotlin.compareTo
import kotlin.toString

class PastOrdersFilterActivity : BaseActivity() {

    private lateinit var binding: ActivityPastOrdersFilterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPastOrdersFilterBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                if (position > 0 && binding.edtPhone.text?.isNotEmpty() == true) {
                    binding.edtPhone.text?.clear()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.edtPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty() && binding.spnDateRange.selectedItemPosition > 0) {
                    binding.spnDateRange.setSelection(0)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // onCreate içinde:
        binding.btnSearch.setOnClickListener {
            val phone = binding.edtPhone.text?.toString()?.trim().orEmpty()
            val selectedIndex = binding.spnDateRange.selectedItemPosition
            val usePhone = phone.isNotEmpty()
            val useDateRange = selectedIndex > 0

            if (usePhone == useDateRange) {
                Toast.makeText(this, "Sadece telefon numarası ya da tarih aralığı ile arama yapabilirsiniz", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val merchantUid = FirebaseAuth.getInstance().currentUser?.uid

            if (usePhone) {
                val intent = Intent(this, SearchResultsActivity::class.java).apply {
                    putExtra("merchantUid", merchantUid)
                    putExtra("phone", phone)
                }
                startActivity(intent)
            } else {
                // Tarih aralığı \- bugün referans alınır
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
