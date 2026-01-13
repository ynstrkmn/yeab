// kotlin
package com.yeab.esnapp.ui.order

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.*
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityNewOrderBinding
import com.yeab.esnapp.model.MerchantUser
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.toString

class NewOrderActivity : BaseActivity() {

    private lateinit var binding: ActivityNewOrderBinding
    private val dbRef = FirebaseDatabase.getInstance().reference
    private var merchantUid: String? = null

    // State preservation için anahtar
    private val KEY_IS_PAYMENT_DONE = "key_is_payment_done"

    // FULL RES fotoğraf URI'si
    private var photoUri: android.net.Uri? = null

    // Geçici olarak tutulan OCR metni
    private var tempRecognizedText: String = ""

    // -- CONTACT PICKER START --
    private lateinit var contactLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestContactPermissionLauncher: ActivityResultLauncher<String>
    // -- CONTACT PICKER END --

    // Kamera sonucu
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = photoUri
                if (uri == null) {
                    Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                try {
                    // Sadece OCR kontrolü için Bitmap oluşturuyoruz
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            processImageForOcr(bitmap)
                        } else {
                            Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.error_generic) + ": " + e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

    // Kamera izni sonucu
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openCameraForProduct()
            } else {
                Toast.makeText(this, getString(R.string.error_camera_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityNewOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        savedInstanceState?.let {
            val isPaymentDone = it.getBoolean(KEY_IS_PAYMENT_DONE, false)
            binding.chkPaymentDone.isChecked = isPaymentDone
        }

        // Rehber seçici launcher
        contactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    binding.phoneInputComponent.setPhoneNumberFromUri(uri)
                    searchCustomer()
                }
            }
        }

        // İzin isteği launcher
        requestContactPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pickContact()
            } else {
                Toast.makeText(this, getString(R.string.error_permission_required), Toast.LENGTH_SHORT).show()
            }
        }

        binding.phoneInputComponent.onPickContactClick = {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                pickContact()
            } else {
                requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }

        binding.btnSearchPhone.setOnClickListener {
            searchCustomer()
        }

        binding.btnAddProduct.setOnClickListener {
            startAddProductFlow()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_PAYMENT_DONE, binding.chkPaymentDone.isChecked)
    }

    private fun clearInformationsArea(){
        binding.edtName.setText("")
        binding.edtSurname.setText("")
        binding.edtEmail.setText("")
    }

    private fun searchCustomer() {
        clearInformationsArea()
        val phone = binding.phoneInputComponent.getPhoneNumber()
        val uid = merchantUid ?: return

        if (phone.length != 10) {
            Toast.makeText(this, getString(R.string.error_phone_10_digits), Toast.LENGTH_SHORT).show()
            return
        }

        showLoading()

        dbRef.child(FirebasePaths.MERCHANTS_USERS)
            .child(uid)
            .child(phone)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    hideLoading()
                    if (snapshot.exists()) {
                        val user = snapshot.getValue(MerchantUser::class.java)
                        user?.let {
                            binding.edtName.setText(it.Name)
                            binding.edtSurname.setText(it.Surname)
                            binding.edtEmail.setText(it.Email)
                        }
                    } else {
                        Toast.makeText(this@NewOrderActivity, getString(R.string.error_customer_not_found), Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    hideLoading()
                    Toast.makeText(this@NewOrderActivity, error.message, Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun startAddProductFlow() {
        val phone = binding.phoneInputComponent.getPhoneNumber()
        val name = binding.edtName.text.toString().trim()
        val surname = binding.edtSurname.text.toString().trim()
        val email = binding.edtEmail.text.toString().trim()
        val desc = binding.edtProductDesc.text.toString().trim()

        if (phone.length != 10 || name.isEmpty() || surname.isEmpty() || email.isEmpty() || desc.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        checkCameraPermissionAndOpen()
    }

    private fun checkCameraPermissionAndOpen() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            openCameraForProduct()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCameraForProduct() {
        val photoFile = File(
            cacheDir,
            "neworder_${System.currentTimeMillis()}.jpg"
        )

        photoUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            photoFile
        )

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        cameraLauncher.launch(cameraIntent)
    }

    private fun pickContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactLauncher.launch(intent)
    }

    /**
     * Resim çekildikten sonra:
     * 1) ML Kit ile üzerindeki metni tanı (Kalite kontrolü amaçlı)
     * 2) Tanınan metin varsa diğer sayfaya geç
     */
    private fun processImageForOcr(bitmap: Bitmap) {
        showLoading()

        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                hideLoading()
                val recognizedText = visionText.text
                if (recognizedText.isNullOrBlank()) {
                    Toast.makeText(this, "Çektiğiniz fotoğraf düzgün alınmadı, lütfen ürünü daha net bir şekilde tekrar çekin.", Toast.LENGTH_LONG).show()
                    openCameraForProduct()
                } else {
                    tempRecognizedText = recognizedText
                    goToMessageTemplateScreen()
                }
            }
            .addOnFailureListener {
                hideLoading()
                Toast.makeText(this, "Çektiğiniz fotoğraf düzgün alınmadı, lütfen ürünü daha net bir şekilde tekrar çekin.", Toast.LENGTH_LONG).show()
                FirebaseCrashlytics.getInstance().recordException(Throwable("OCR failed: ${it.message}"))
                openCameraForProduct()
            }
    }

    private fun goToMessageTemplateScreen() {
        val intent = Intent(this, MessageTemplateActivity::class.java)
        intent.putExtra(IntentKeys.MERCHANT_UID, merchantUid)
        intent.putExtra(IntentKeys.PHONE, binding.phoneInputComponent.getPhoneNumber())
        intent.putExtra(IntentKeys.NAME, binding.edtName.text.toString().trim())
        intent.putExtra(IntentKeys.SURNAME, binding.edtSurname.text.toString().trim())
        intent.putExtra(IntentKeys.EMAIL, binding.edtEmail.text.toString().trim())
        intent.putExtra(IntentKeys.PRODUCT_DESC, binding.edtProductDesc.text.toString().trim())
        intent.putExtra(IntentKeys.IS_PAYMENT_DONE, binding.chkPaymentDone.isChecked)

        // ÖNEMLİ: Upload edilmemiş yerel dosya yolunu gönderiyoruz
        if (photoUri != null) {
            intent.putExtra("extra_local_photo_uri", photoUri.toString())
        }
        intent.putExtra("extra_recognized_text", tempRecognizedText)

        // Order ID'yi burada oluşturmuyoruz, diğer tarafta oluşturulacak veya null gidecek

        startActivity(intent)
        finish()
    }
}
