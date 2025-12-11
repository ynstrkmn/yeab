package com.yeab.esnapp.ui.auth

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityMerchantInfoBinding
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.ui.home.HomeActivity
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MerchantInfoActivity : BaseActivity() {

    private lateinit var binding: ActivityMerchantInfoBinding
    private val dbRef = FirebaseDatabase.getInstance().reference
    private var merchantUid: String? = null
    private var isLocationFetched = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // MapPicker launcher: MapPickerActivity'den dönen değerleri alır
    private val mapPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val data = result.data!!
                val lat = data.getDoubleExtra(MapPickerActivity.EXTRA_LAT, Double.NaN)
                val lng = data.getDoubleExtra(MapPickerActivity.EXTRA_LNG, Double.NaN)
                val country = data.getStringExtra(MapPickerActivity.EXTRA_COUNTRY) ?: ""
                val city = data.getStringExtra(MapPickerActivity.EXTRA_CITY) ?: ""
                val district = data.getStringExtra(MapPickerActivity.EXTRA_DISTRICT) ?: ""

                // Koordinat alanı
                if (!lat.isNaN() && !lng.isNaN()) {
                    binding.txtCoordinates.text = String.format(Locale.US, "%.6f, %.6f", lat, lng)
                }

                // Ülke / İl / İlçe alanlarını doldur (read-only edittext'lere yaz)
                binding.edtCountry.setText(country)
                binding.edtCity.setText(city)
                binding.edtDistrict.setText(district)
                isLocationFetched = true

            }
        }

    // Lokasyon izinleri için launcher: izin verildiğinde MapPicker başlatılır
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarse = perms[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fine || coarse) {
                val i = Intent(this, MapPickerActivity::class.java)
                mapPickerLauncher.launch(i)
            } else {
                Toast.makeText(this, getString(R.string.error_location_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMerchantInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // DatePicker için click listener (edtBirthDate zaten non-focusable; click ile dialog açılıyor)
        binding.edtBirthDate.setOnClickListener { showDatePicker() }

        // Konum seç butonu: önce izin kontrol, sonra MapPicker
        binding.btnSelectLocation.setOnClickListener {
            val fineGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val coarseGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (fineGranted || coarseGranted) {
                val i = Intent(this, MapPickerActivity::class.java)
                mapPickerLauncher.launch(i)
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        }

        binding.btnSave.setOnClickListener { saveMerchant() }

        binding.edtCountry.setOnClickListener {
            if(!isLocationFetched)
                disabledFieldInfo()
            else
                locationChangeGoTakeLocButton()
        }
        binding.edtCity.setOnClickListener {
            if(!isLocationFetched)
                disabledFieldInfo()
            else
                locationChangeGoTakeLocButton()
        }
        binding.edtDistrict.setOnClickListener {
            if(!isLocationFetched)
                disabledFieldInfo()
            else
                locationChangeGoTakeLocButton()
        }
    }

    val disabledFieldInfo = {
        Toast.makeText(
            this,
            getString(R.string.info_select_location_first),
            Toast.LENGTH_LONG
        ).show()
    }

    val locationChangeGoTakeLocButton = {
        Toast.makeText(
            this,
            getString(R.string.info_select_location_first),
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * DatePicker dialog gösterir. Varsayılan tarih bugün; eğer edtBirthDate'ta daha önce bir tarih varsa onu parse edip başlangıç tarihini o yapar.
     */
    private fun showDatePicker() {
        try {
            val currentCalendar = Calendar.getInstance()

            val existing = binding.edtBirthDate.text?.toString()?.trim()
            if (!existing.isNullOrEmpty()) {
                try {
                    val parser = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    val d = parser.parse(existing)
                    if (d != null) currentCalendar.time = d
                } catch (_: Exception) { /* ignore */ }
            }

            val year = currentCalendar.get(Calendar.YEAR)
            val month = currentCalendar.get(Calendar.MONTH)
            val day = currentCalendar.get(Calendar.DAY_OF_MONTH)

            val dpd = DatePickerDialog(this, { _, y, m, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(y, m, dayOfMonth)
                val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                binding.edtBirthDate.setText(fmt.format(cal.time))
            }, year, month, day)

            dpd.datePicker.maxDate = System.currentTimeMillis()
            dpd.show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("ControlFlowWithEmptyBody")
    @SuppressLint("MissingPermission")
    private fun fetchLastLocation() {
        // not used in current flow, kept for reference if needed
        showLoading()
        fusedLocationClient.lastLocation
            .addOnSuccessListener { loc: Location? ->
                hideLoading()
                if (loc != null) {
                    val coords = String.format(Locale.US, "%.6f, %.6f", loc.latitude, loc.longitude)
                    binding.txtCoordinates.text = coords
                } else {
                    Toast.makeText(this, getString(R.string.error_location_unavailable), Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { err ->
                hideLoading()
                Toast.makeText(this, getString(R.string.error_location_unavailable) + ": ${err.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Merchant'ı kaydeder. Firebase'de Merchant objesi yerine HashMap kullanıyorum
     * => model farklıysa hata almamak için.
     */
    private fun saveMerchant() {
        val birthDate = binding.edtBirthDate.text.toString().trim()
        val coords = binding.txtCoordinates.text.toString().trim()
        val merchantName = binding.edtMerchantName.text.toString().trim()
        val merchantType = binding.edtMerchantType.text.toString().trim()
        val mobile = binding.edtMobile.text.toString().trim()
        val name = binding.edtName.text.toString().trim()
        val surname = binding.edtSurname.text.toString().trim()

        val country = binding.edtCountry.text.toString().trim()
        val city = binding.edtCity.text.toString().trim()
        val district = binding.edtDistrict.text.toString().trim()

        if (birthDate.isEmpty() || coords.isEmpty() || merchantName.isEmpty()
            || merchantType.isEmpty() || mobile.isEmpty() || name.isEmpty() || surname.isEmpty()
            || country.isEmpty() || city.isEmpty() || district.isEmpty()
        ) {
            Toast.makeText(this, getString(R.string.error_fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        val uid = merchantUid ?: return

        showLoading()
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = if (task.isSuccessful) task.result else null

            // Map ile kaydet -> model mismatch sorununu önler
            val merchantMap = hashMapOf<String, Any?>(
                "BirthDate" to birthDate,
                "Country" to country,
                "City" to city,
                "Coordinates" to coords,
                "District" to district,
                "MerchantName" to merchantName,
                "MerchantType" to merchantType,
                "MobilePhoneNumber" to try { mobile.toLong() } catch (e: Exception) { 0L },
                "Name" to name,
                "Surname" to surname,
                "PushToken" to token
            )

            dbRef.child(FirebasePaths.MERCHANTS)
                .child(uid)
                .setValue(merchantMap)
                .addOnSuccessListener {
                    hideLoading()
                    Toast.makeText(this, getString(R.string.info_merchant_saved), Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, HomeActivity::class.java)
                    intent.putExtra(IntentKeys.MERCHANT_UID, uid)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener {
                    hideLoading()
                    Toast.makeText(this, it.message ?: getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                }
        }
    }
}
