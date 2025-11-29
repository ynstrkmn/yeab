package com.yeab.esnapp.ui.order

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityNewOrderBinding
import com.yeab.esnapp.model.MerchantUser
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.math.BigInteger
import java.util.UUID

class NewOrderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewOrderBinding
    private val dbRef = FirebaseDatabase.getInstance().reference
    private val storageRef = FirebaseStorage.getInstance().reference

    private var merchantUid: String? = null
    private var productImageUrl: String? = null
    private var photoUri: Uri? = null


    // Kamera sonucu
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Full resolution bitmapi buradan okuyoruz
                val uri = photoUri ?: return@registerForActivityResult
                val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))

                if (bitmap != null) {
                    uploadImage(bitmap)  // <-- MLKit için artık yüksek çözünürlük
                } else {
                    Toast.makeText(this, "Fotoğraf okunamadı", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.error_phone_10_digits), Toast.LENGTH_SHORT)
                .show()
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
            Toast.makeText(this, getString(R.string.error_fill_all_fields), Toast.LENGTH_SHORT)
                .show()
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
        val photoFile = File(
            cacheDir,
            "photo_${System.currentTimeMillis()}.jpg"
        )

        photoUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            photoFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        cameraLauncher.launch(intent)
    }

    /**
     * Resim çekildikten sonra:
     * 1) ML Kit ile üzerindeki metni tanı
     * 2) Tanınan metni ve resmi Firebase Storage + Realtime DB'ye yaz
     * 3) Mesaj şablonu ekranına geç
     */
    private fun uploadImage(bitmap: Bitmap) {
        val uid = merchantUid ?: return

        // 1) ML Kit Text Recognition ile resmi oku
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val recognizedText = visionText.text ?: ""
                uploadImageInternal(uid, bitmap, recognizedText)
            }
            .addOnFailureListener {
                // OCR başarısız olursa da siparişi bozma; sadece recognizedText boş gitsin
                uploadImageInternal(uid, bitmap, "")
            }
    }

    /**
     * Gerçek upload + meta kaydı burada
     */
    private fun uploadImageInternal(
        uid: String,
        bitmap: Bitmap,
        recognizedText: String
    ) {
        val imageId = UUID.randomUUID().toString()
        val fileName = "orders/$uid/$imageId.jpg"
        val imgRef = storageRef.child(fileName)

        val baos = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val data = baos.toByteArray()

        // SHA-256 hash
        val sha256 = try {
            sha256Hex(data)
        } catch (e: Exception) {
            ""
        }

        // Eski phash yapını bozmayalım, ileride tekrar lazım olabilir
        val phash = try {
            averageHash(bitmap)
        } catch (e: Exception) {
            ""
        }

        imgRef.putBytes(data)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: Exception("Upload failed")
                }
                imgRef.downloadUrl
            }
            .addOnSuccessListener { uri ->
                productImageUrl = uri.toString()

                // Database'e kaydedilecek metadata
                val meta = HashMap<String, Any?>()
                meta["imageUrl"] = productImageUrl
                meta["hash"] = sha256
                meta["phash"] = phash
                meta["fileName"] = fileName
                meta["timestamp"] = ServerValue.TIMESTAMP
                // 🔴 YENİ: ML Kit ile okunan metni de kaydediyoruz
                meta["recognizedText"] = recognizedText

                dbRef.child("image_hashes")
                    .child(uid)
                    .child(imageId)
                    .setValue(meta)
                    .addOnCompleteListener {
                        // Meta yazımı tamamlandıktan sonra şablon ekranına geç
                        goToMessageTemplateScreen()
                    }

            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    getString(R.string.error_image_upload) + ": " + (it.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // Eski averageHash duruyor; ister kullanırsın ister ileride temizlersin
    private fun averageHash(src: Bitmap): String {
        val size = 8
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)

        val luminances = IntArray(pixels.size)
        var sum = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            luminances[i] = lum
            sum += lum
        }
        val avg = sum / luminances.size

        val bits = StringBuilder()
        for (lum in luminances) {
            bits.append(if (lum >= avg) '1' else '0')
        }

        val bigInt = BigInteger(bits.toString(), 2)
        return String.format("%016x", bigInt)
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
