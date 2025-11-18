package com.yeab.esnapp.ui.order

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityNewOrderBinding
import com.yeab.esnapp.model.MerchantUser
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys
import java.util.UUID

class NewOrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewOrderBinding
    private val dbRef = FirebaseDatabase.getInstance().reference
    private val storageRef = FirebaseStorage.getInstance().reference

    private var merchantUid: String? = null
    private var productImageUrl: String? = null

    // Kamera sonucu
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val bitmap = result.data!!.extras?.get("data") as? android.graphics.Bitmap
                if (bitmap != null) {
                    uploadImage(bitmap)
                }
            }
        }

    // Kamera izni sonucu
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openCameraForProduct()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.error_camera_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)

        binding.btnSearchPhone.setOnClickListener {
            searchCustomer()
        }

        binding.btnAddProduct.setOnClickListener {
            startAddProductFlow()
        }
    }

    private fun searchCustomer() {
        val phone = binding.edtPhone.text.toString().trim()
        val uid = merchantUid ?: return

        if (phone.length != 10) {
            Toast.makeText(this, getString(R.string.error_phone_10_digits), Toast.LENGTH_SHORT).show()
            return
        }

        dbRef.child(FirebasePaths.MERCHANTS_USERS)
            .child(uid)
            .child(phone)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val user = snapshot.getValue(MerchantUser::class.java)
                        user?.let {
                            binding.edtName.setText(it.Name)
                            binding.edtSurname.setText(it.Surname)
                            binding.edtEmail.setText(it.Email)
                        }
                    } else {
                        Toast.makeText(
                            this@NewOrderActivity,
                            getString(R.string.error_customer_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun startAddProductFlow() {
        val phone = binding.edtPhone.text.toString().trim()
        val name = binding.edtName.text.toString().trim()
        val surname = binding.edtSurname.text.toString().trim()
        val email = binding.edtEmail.text.toString().trim()
        val desc = binding.edtProductDesc.text.toString().trim()

        if (phone.length != 10 || name.isEmpty() || surname.isEmpty() || email.isEmpty() || desc.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_fill_all_fields), Toast.LENGTH_SHORT).show()
            return
        }

        // Burada direkt kamera açmak yerine önce izin kontrolü yapıyoruz
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
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(cameraIntent)
    }

    private fun uploadImage(bitmap: android.graphics.Bitmap) {
        val uid = merchantUid ?: return
        val fileName = "orders/$uid/${UUID.randomUUID()}.jpg"
        val imgRef = storageRef.child(fileName)

        val baos = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, baos)
        val data = baos.toByteArray()

        imgRef.putBytes(data)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: Exception("Upload failed")
                }
                imgRef.downloadUrl
            }
            .addOnSuccessListener { uri ->
                productImageUrl = uri.toString()
                goToMessageTemplateScreen()
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    getString(R.string.error_image_upload) + ": " + (it.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun goToMessageTemplateScreen() {
        val intent = Intent(this, MessageTemplateActivity::class.java)
        intent.putExtra(IntentKeys.MERCHANT_UID, merchantUid)
        intent.putExtra(IntentKeys.PHONE, binding.edtPhone.text.toString().trim())
        intent.putExtra(IntentKeys.NAME, binding.edtName.text.toString().trim())
        intent.putExtra(IntentKeys.SURNAME, binding.edtSurname.text.toString().trim())
        intent.putExtra(IntentKeys.EMAIL, binding.edtEmail.text.toString().trim())
        intent.putExtra(IntentKeys.PRODUCT_DESC, binding.edtProductDesc.text.toString().trim())
        intent.putExtra(IntentKeys.PRODUCT_IMAGE_URL, productImageUrl)
        startActivity(intent)
    }
}
