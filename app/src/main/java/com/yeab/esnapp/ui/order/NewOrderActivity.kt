package com.yeab.esnapp.ui.order

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.*
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityNewOrderBinding
import com.yeab.esnapp.model.MerchantUser
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.util.FileUtils
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.launch
import java.io.File

class NewOrderActivity : BaseActivity() {

    private lateinit var binding: ActivityNewOrderBinding
    private val dbRef = FirebaseDatabase.getInstance().reference
    private var merchantUid: String? = null
    private val KEY_IS_PAYMENT_DONE = "key_is_payment_done"

    // -- CONTACT PICKER START --
    private lateinit var contactLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestContactPermissionLauncher: ActivityResultLauncher<String>
    // -- CONTACT PICKER END --

    private var lastOrderNumber: String? = null

    // 1. DÜZELTME: Burası artık kamerayı açmıyor, gelen fotoğrafı işliyor
    private val orderPreparationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                lastOrderNumber = data?.getStringExtra(IntentKeys.ORDER_NUMBER)
                val capturedUriString = data?.getStringExtra("captured_image_uri")
                val recognizedText = data?.getStringExtra(IntentKeys.RECOGNIZED_TEXT)

                if (capturedUriString != null) {
                    // Otomatik çekilen fotoğrafın URI'si geldi
                    val photoUri = Uri.parse(capturedUriString)

                    // Direkt sıkıştırma ve geçiş işlemine başla (Tekrar kamera açma!)
                    customCompressImageFromCamera(FileUtils.from(this, photoUri), photoUri, recognizedText)
                } else {
                    Toast.makeText(this, "Fotoğraf alınamadı", Toast.LENGTH_SHORT).show()
                }
            }
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

        val intent = Intent(this, OrderPreparationActivity::class.java).apply {
            putExtra(IntentKeys.MERCHANT_UID, merchantUid)
        }
        orderPreparationLauncher.launch(intent)
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

        setupContactPickers()

        binding.btnSearchPhone.setOnClickListener { searchCustomer() }
        binding.btnAddProduct.setOnClickListener { startAddProductFlow() }
    }

    private fun setupContactPickers() {
        contactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    binding.phoneInputComponent.setPhoneNumberFromUri(uri)
                    searchCustomer()
                }
            }
        }

        requestContactPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pickContact() else Toast.makeText(this, getString(R.string.error_permission_required), Toast.LENGTH_SHORT).show()
        }

        binding.phoneInputComponent.onPickContactClick = {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                pickContact()
            } else {
                requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
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

    private fun pickContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactLauncher.launch(intent)
    }

    private var compressedImage: File? = null

    // 2. DÜZELTME: Parametre olarak orijinal URI'yi de alıyoruz ki diğer tarafa gönderelim
    private fun customCompressImageFromCamera(actualImage: File?, originalUri: Uri, recognizedText: String?) {
        actualImage?.let { imageFile ->
            lifecycleScope.launch {
                compressedImage = Compressor.compress(this@NewOrderActivity, imageFile) {
                    resolution(980, 1280)
                    quality(40)
                    format(Bitmap.CompressFormat.JPEG)
                    size(2_097_152) // 2 MB
                }
                goToMessageTemplateScreen(originalUri, recognizedText)
            }
        } ?: showError("Resim işlenemedi!")
    }

    private fun showError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    private fun goToMessageTemplateScreen(originalUri: Uri, recognizedText: String?) {
        val intent = Intent(this, MessageTemplateActivity::class.java)
        intent.putExtra(IntentKeys.MERCHANT_UID, merchantUid)
        intent.putExtra(IntentKeys.PHONE, binding.phoneInputComponent.getPhoneNumber())
        intent.putExtra(IntentKeys.NAME, binding.edtName.text.toString().trim())
        intent.putExtra(IntentKeys.SURNAME, binding.edtSurname.text.toString().trim())
        intent.putExtra(IntentKeys.EMAIL, binding.edtEmail.text.toString().trim())
        intent.putExtra(IntentKeys.PRODUCT_DESC, binding.edtProductDesc.text.toString().trim())
        intent.putExtra(IntentKeys.IS_PAYMENT_DONE, binding.chkPaymentDone.isChecked)
        intent.putExtra(IntentKeys.ORDER_NUMBER, lastOrderNumber ?: "-")

        // Orijinal (Otomatik çekilen) fotoğrafı gönderiyoruz
        intent.putExtra("extra_local_photo_uri", originalUri.toString())

        // Not: OCR metni artık AutoCapture'da işlendiği için buraya boş veya oradan gelen veriyle doldurulabilir.
        // Şimdilik boş gönderiyoruz, önemli olan fotoğraf.
        intent.putExtra("extra_recognized_text", recognizedText ?: "")

        startActivity(intent)
        finish()
    }
}