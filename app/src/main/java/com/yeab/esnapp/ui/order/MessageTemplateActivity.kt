package com.yeab.esnapp.ui.order

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.google.android.material.chip.Chip
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityMessageTemplateBinding
import com.yeab.esnapp.model.MerchantMessageTemplate
import com.yeab.esnapp.model.MerchantUser
import com.yeab.esnapp.model.Order
import com.yeab.esnapp.model.ProductStatus
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.ui.messages.MerchantMessageTemplatesActivity
import com.yeab.esnapp.util.DateFormats
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys
import com.yeab.esnapp.util.MerchantSession
import com.yeab.esnapp.util.WhatsAppUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.*

class MessageTemplateActivity : BaseActivity() {

    private lateinit var binding: ActivityMessageTemplateBinding
    private val dbRef = FirebaseDatabase.getInstance().reference
    private val storageRef = FirebaseStorage.getInstance().reference

    private var merchantUid: String? = null
    private lateinit var phone: String
    private var name: String = ""
    private var surname: String = ""
    private var email: String = ""
    private var productDesc: String = ""
    private var isPaymentDone: Boolean = false
    private var paymentDate: String = ""

    // --- RESİM DEĞİŞKENLERİ ---
    // 1. Asıl Resim (Otomatik - GİZLİ)
    private var localPhotoUriStr: String? = null
    private var productImageUrl: String? = null

    // 2. Ekstra Resim (Manuel - GÖRÜNÜR)
    private var manualPhotoUri: Uri? = null
    private var productAdditionalmageUrl: String? = null

    private var recognizedText: String = ""

    private var customerNameSurname: String = ""
    private val templateMap = mutableMapOf<Int, MerchantMessageTemplate>()
    private var orderIdOrigin: String = ""
    private lateinit var imgOrderPhoto: ImageView
    private var orderNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMessageTemplateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Verileri Al
        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)
        phone = intent.getStringExtra(IntentKeys.PHONE) ?: ""
        name = intent.getStringExtra(IntentKeys.NAME) ?: ""
        surname = intent.getStringExtra(IntentKeys.SURNAME) ?: ""
        email = intent.getStringExtra(IntentKeys.EMAIL) ?: ""
        productDesc = intent.getStringExtra(IntentKeys.PRODUCT_DESC) ?: ""
        isPaymentDone = intent.getBooleanExtra(IntentKeys.IS_PAYMENT_DONE, false)

        // NewOrderActivity'den gelen otomatik fotoğraf yolu (Sadece upload için tutuyoruz)
        localPhotoUriStr = intent.getStringExtra("extra_local_photo_uri")
        recognizedText = intent.getStringExtra("extra_recognized_text") ?: ""

        orderIdOrigin = intent.getStringExtra(IntentKeys.ORDER_ID) ?: ""
        orderNumber = intent.getStringExtra(IntentKeys.ORDER_NUMBER) ?: ""

        if (orderIdOrigin.isEmpty()) {
            orderIdOrigin = System.currentTimeMillis().toString()
        }

        customerNameSurname = listOf(name, surname).filter { it.isNotEmpty() }.joinToString(" ")

        // UI Bağlantıları
        imgOrderPhoto = findViewById(R.id.imgOrderPhoto)
        val btnCamera = findViewById<Button>(R.id.btnCamera)

        // Otomatik fotoğrafı GİZLİ tutuyoruz
        imgOrderPhoto.visibility = View.GONE

        // Ekstra Fotoğraf Butonu
        btnCamera.visibility = View.VISIBLE
        btnCamera.text = "Ekstra Fotoğraf Ekle"
        btnCamera.setOnClickListener {
            startManualCamera()
        }

        binding.txtTitle.text = getString(R.string.message_template_title)
        binding.btnSaveOrder.text = getString(R.string.message_template_button_save_order)
        binding.edtFreeText.hint = getString(R.string.message_template_hint_free_text)

        binding.btnMessageTemplates.setOnClickListener {
            val i = Intent(this, MerchantMessageTemplatesActivity::class.java)
            i.putExtra(IntentKeys.MERCHANT_UID, merchantUid)
            startActivity(i)
        }

        binding.radioGroupTemplates.isSingleSelection = true
        loadTemplates()

        binding.btnSaveOrder.setOnClickListener {
            onCompleteClicked()
        }

        setupChipGroupListener()
    }

    // --- MANUEL KAMERA (EKSTRA FOTOĞRAF) ---
    private fun startManualCamera() {
        manualPhotoUri = createImageUri(this)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, manualPhotoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        manualCameraLauncher.launch(cameraIntent)
    }

    private val manualCameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // SADECE BURADA FOTOĞRAF GÖSTERİLİYOR
            // Kullanıcı ekstra fotoğraf çektiyse göster
            imgOrderPhoto.setImageURI(manualPhotoUri)
            imgOrderPhoto.visibility = View.VISIBLE
            Toast.makeText(this, "Ekstra fotoğraf eklendi.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageUri(context: Context): Uri {
        val imageFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "extra_photo_${System.currentTimeMillis()}.jpg"
        )
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    }

    // --- KAYDETME SÜRECİ ---
    private fun onCompleteClicked() {
        val uid = merchantUid ?: return
        if (phone.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
            return
        }

        val selectedChipId = binding.radioGroupTemplates.checkedChipId
        val selectedTemplateText = if (selectedChipId != -1) {
            binding.radioGroupTemplates.findViewById<Chip>(selectedChipId)?.text?.toString()
        } else null

        val freeText = binding.edtFreeText.text.toString().trim()

        val messageText = when {
            !selectedTemplateText.isNullOrEmpty() -> selectedTemplateText
            freeText.isNotEmpty() -> freeText
            else -> {
                Toast.makeText(this, getString(R.string.error_no_message_selected), Toast.LENGTH_SHORT).show()
                return
            }
        }

        verifyOrderNumberThenProcess(uid, messageText)
    }

    private fun verifyOrderNumberThenProcess(uid: String, messageText: String) {
        showLoading()
        dbRef.child(FirebasePaths.MERCHANTS)
            .child(uid)
            .child("OrderNumber")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val value = snapshot.value

                    // Veritabanındaki (en son kaydedilen) numara
                    val lastDbOrderNumber: Long = when (value) {
                        is String -> value.toLongOrNull() ?: 0L
                        is Number -> value.toLong()
                        else -> 0L
                    }

                    // Bizim elimizdeki (kaydetmeye çalıştığımız) yeni numara
                    val myCurrentOrderNumber: Long = orderNumber.toLongOrNull() ?: 0L

                    // DÜZELTİLDİ: DB'deki son numaranın 1 fazlası bizimkine eşit olmalı
                    if ((lastDbOrderNumber + 1) == myCurrentOrderNumber) {
                        // SİPARİŞ NUMARASI DOĞRU, YÜKLEMEYE BAŞLA
                        // 1. Önce Asıl (GİZLİ) Resmi Yükle
                        startMainPhotoUpload(uid, messageText)
                    } else {
                        hideLoading()
                        Toast.makeText(this@MessageTemplateActivity,
                            "Sıra hatası! Beklenen: ${lastDbOrderNumber + 1}, Gelen: $myCurrentOrderNumber. Lütfen yeniden deneyin.",
                            Toast.LENGTH_LONG).show()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    hideLoading()
                    Toast.makeText(this@MessageTemplateActivity, error.message, Toast.LENGTH_SHORT).show()
                }
            })
    }

    // --- 1. ASIL RESMİ YÜKLE (GİZLİ OLAN) ---
    private fun startMainPhotoUpload(uid: String, messageText: String) {
        if (localPhotoUriStr.isNullOrEmpty()) {
            // Asıl resim yoksa direkt ekstra resme geç
            startExtraPhotoUpload(uid, messageText)
            return
        }

        // isMain = true
        uploadPhotoToFirebase(uid, Uri.parse(localPhotoUriStr!!), true) { url ->
            productImageUrl = url // URL'i kaydet (Veritabanına gidecek ana resim)

            // Asıl resim için hash ve metadata kaydı
            saveImageMetadata(uid, url) {
                // Asıl resim bitti, şimdi ekstra resme geç
                startExtraPhotoUpload(uid, messageText)
            }
        }
    }

    // --- 2. EKSTRA RESMİ YÜKLE (GÖRÜNÜR OLAN) ---
    private fun startExtraPhotoUpload(uid: String, messageText: String) {
        if (manualPhotoUri == null) {
            // Ekstra resim çekilmemiş, direkt kaydetmeye geç
            processOrderSave(uid, messageText)
            return
        }

        // isMain = false
        uploadPhotoToFirebase(uid, manualPhotoUri!!, false) { url ->
            productAdditionalmageUrl = url // URL'i kaydet (Veritabanına gidecek ek resim)

            // Her şey bitti, veritabanına kaydet
            processOrderSave(uid, messageText)
        }
    }

    // --- YARDIMCI: FOTOĞRAF YÜKLEME ---
    private fun uploadPhotoToFirebase(uid: String, uri: Uri, isMain: Boolean, onComplete: (String?) -> Unit) {
        val bitmap = try {
            val inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) { null }

        if (bitmap == null) {
            onComplete(null)
            return
        }

        val imageId = UUID.randomUUID().toString()
        val suffix = if (isMain) "main" else "extra"
        val fileName = "orders/$uid/${orderIdOrigin}_${suffix}_$imageId.jpg"
        val imgRef = storageRef.child(fileName)

        val correctedBitmap = try { fixBitmapOrientation(bitmap, uri) } catch (e: Exception) { bitmap }

        val baos = ByteArrayOutputStream()
        // Kaliteyi %70'e çekiyoruz, hem hızlı hem yeterli kalite
        correctedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val data = baos.toByteArray()

        imgRef.putBytes(data)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                imgRef.downloadUrl
            }
            .addOnSuccessListener { downloadUrl ->
                onComplete(downloadUrl.toString())
            }
            .addOnFailureListener {
                // Hata olsa da akışı bozma, null dön
                onComplete(null)
            }
    }

    private fun saveImageMetadata(uid: String, url: String?, onDone: () -> Unit) {
        if (url == null) {
            onDone()
            return
        }
        val meta = HashMap<String, Any?>()
        meta["imageUrl"] = url
        meta["timestamp"] = ServerValue.TIMESTAMP
        meta["recognizedText"] = recognizedText
        meta["orderNumber"] = orderNumber

        dbRef.child("image_hashes")
            .child(uid)
            .child(orderIdOrigin)
            .setValue(meta)
            .addOnCompleteListener { onDone() }
    }

    // --- 3. VERİTABANI KAYDI ---
    private fun processOrderSave(uid: String, messageText: String) {
        saveCustomerIfNeeded(uid) {
            saveOrderAndSendWhatsApp(uid, orderIdOrigin, messageText)
        }
    }

    private fun saveCustomerIfNeeded(uid: String, onDone: () -> Unit) {
        val userRef = dbRef.child(FirebasePaths.MERCHANTS_USERS).child(uid).child(phone)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val user = MerchantUser().apply {
                        MobilePhoneNumber = phone.toLongOrNull() ?: 0L
                        Name = name
                        Surname = surname
                        Email = email
                    }
                    userRef.setValue(user).addOnCompleteListener { onDone() }
                } else onDone()
            }
            override fun onCancelled(error: DatabaseError) = onDone()
        })
    }

    private fun saveOrderAndSendWhatsApp(uid: String, orderId: String, messageText: String) {
        val now = Date()
        val isoFormatter = SimpleDateFormat(DateFormats.ORDER_STATUS_ISO, Locale.getDefault())
        val nowIso = isoFormatter.format(now)

        val statusList = mutableListOf(ProductStatus(messageText, nowIso))
        paymentDate = if (isPaymentDone) nowIso else ""

        val order = Order(
            false,
            productImageUrl,          // Asıl Resim (Otomatik - GİZLİ)
            productAdditionalmageUrl, // Ekstra Resim (Varsa - GÖRÜNÜR)
            productDesc,
            statusList,
            nowIso,
            isPaymentDone,
            paymentDate,
            phone,
            orderNumber
        )

        val merchantOrderPath = "${FirebasePaths.ORDERS_ROOT}/${FirebasePaths.ORDERS_MERCHANT_ORDERS}/$uid/$phone/$orderId"
        val userOrderPath = "${FirebasePaths.USER_ORDERS_ROOT}/$phone/$uid/$orderId"

        val updates = hashMapOf<String, Any>(
            merchantOrderPath to order,
            userOrderPath to order
        )

        val dateKey = try {
            val parser = SimpleDateFormat(DateFormats.ORDER_STATUS_ISO, Locale.getDefault())
            val created = parser.parse(order.createdDate!!)
            val ymd = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            ymd.format(created!!)
        } catch (_: Exception) {
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now)
        }

        dbRef.updateChildren(updates).addOnSuccessListener {
            incrementMerchantOrderNumber(uid) {
                hideLoading()
                val displayFormatter = SimpleDateFormat(DateFormats.ORDER_STATUS_DISPLAY, Locale.getDefault())
                val displayDate = displayFormatter.format(now)
                val customerDisplayName = if (customerNameSurname.isNotEmpty()) customerNameSurname else phone
                val safeProductName = if (productDesc.isNotEmpty()) productDesc else getString(R.string.app_name)
                val detailLink = "https://esnaf.online/index.html?merchantId=$uid&orderId=$orderId&orderDate=$dateKey"

                val trLocale = Locale.forLanguageTag("tr-TR")
                val customerDisplayNameUpper = customerDisplayName.uppercase(trLocale)
                val safeProductNameUpper = safeProductName.uppercase(trLocale)
                val messageTextUpper = messageText.uppercase(trLocale)
                val merchantNameUpper = (MerchantSession.merchant?.MerchantName ?: "").uppercase(trLocale)

                val formattedMessage = getString(
                    R.string.whatsapp_status_message,
                    customerDisplayNameUpper,
                    displayDate,
                    orderId,
                    safeProductNameUpper,
                    messageTextUpper,
                    detailLink,
                    merchantNameUpper
                )

                WhatsAppUtils.sendMessage(this, phone, formattedMessage)
                finish()
            }
        }.addOnFailureListener {
            hideLoading()
            Toast.makeText(this, it.message ?: getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
        }
    }

    private fun incrementMerchantOrderNumber(uid: String, onDone: () -> Unit) {
        val ref = dbRef.child(FirebasePaths.MERCHANTS).child(uid).child("OrderNumber")
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val v = currentData.value
                val current = when (v) {
                    is String -> v.toLongOrNull()
                    is Number -> v.toLong()
                    else -> null
                }
                currentData.value = (current ?: 0L) + 1L
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                onDone()
            }
        })
    }

    // --- HELPER FUNCTIONS ---
    private fun fixBitmapOrientation(src: Bitmap, uri: Uri): Bitmap {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val exif = androidx.exifinterface.media.ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = android.graphics.Matrix()
                when (orientation) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                        matrix.postRotate(90f); matrix.preScale(-1f, 1f)
                    }
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                        matrix.postRotate(270f); matrix.preScale(-1f, 1f)
                    }
                }
                Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            } ?: src
        } catch (e: Exception) { src }
    }

    // --- ŞABLON YÖNETİMİ ---
    private fun loadTemplates() {
        val uid = merchantUid ?: return
        showLoading()
        dbRef.child(FirebasePaths.MERCHANT_MESSAGE_TEMPLATES)
            .child(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    hideLoading()
                    binding.radioGroupTemplates.removeAllViews()
                    templateMap.clear()

                    if (snapshot.exists()) binding.btnMessageTemplates.visibility = LinearLayout.GONE
                    else binding.btnMessageTemplates.visibility = LinearLayout.VISIBLE

                    for (child in snapshot.children) {
                        val template = child.getValue(MerchantMessageTemplate::class.java) ?: continue
                        val text = template.Text ?: continue
                        val chip = Chip(this@MessageTemplateActivity).apply {
                            id = View.generateViewId()
                            this.text = text
                            isCheckable = true
                            isClickable = true
                            isCheckedIconVisible = false
                        }
                        binding.radioGroupTemplates.addView(chip)
                        templateMap[chip.id] = template
                    }
                    setupChipGroupListener()
                    resetAllChipStyles()
                }
                override fun onCancelled(error: DatabaseError) { hideLoading() }
            })
    }

    private fun resetAllChipStyles() {
        val defaultBackgroundColor = com.google.android.material.R.attr.colorSurface
        val colorStateList = android.content.res.ColorStateList.valueOf(getThemeColor(defaultBackgroundColor))
        val unSelectedColor = ContextCompat.getColor(this, R.color.chip_unselected_background)
        val selectedStrokeColor = ContextCompat.getColor(this, R.color.chip_selected_stroke)

        for (i in 0 until binding.radioGroupTemplates.childCount) {
            val view = binding.radioGroupTemplates.getChildAt(i)
            if (view is Chip) {
                view.chipBackgroundColor = android.content.res.ColorStateList.valueOf(unSelectedColor)
                view.chipStrokeWidth = 4f
                view.chipStrokeColor = android.content.res.ColorStateList.valueOf(selectedStrokeColor)
            }
        }
    }

    private fun setupChipGroupListener() {
        val selectedColor = ContextCompat.getColor(this, R.color.chip_selected_background)
        val selectedStrokeColor = ContextCompat.getColor(this, R.color.chip_selected_stroke)

        val selectedTextSizeSp = 22f
        val unselectedTextSizeSp = 20f

        // Başlangıçta tüm Chip'lere varsayılan metin boyutu ve ellipsize uygula
        for (i in 0 until binding.radioGroupTemplates.childCount) {
            (binding.radioGroupTemplates.getChildAt(i) as? Chip)?.apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, unselectedTextSizeSp)
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        }

        binding.radioGroupTemplates.setOnCheckedStateChangeListener { group, checkedIds ->
            resetAllChipStyles()

            // Tüm Chip'leri unselected boyuta döndür
            for (i in 0 until group.childCount) {
                (group.getChildAt(i) as? Chip)?.apply {
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, unselectedTextSizeSp)
                    requestLayout()
                }
            }

            if (checkedIds.isNotEmpty()) {
                binding.edtFreeText.isEnabled = false
                val selectedChipId = checkedIds.first()
                val selectedChip = group.findViewById<Chip>(selectedChipId)
                selectedChip?.apply {
                    chipBackgroundColor = android.content.res.ColorStateList.valueOf(selectedColor)
                    chipStrokeWidth = 4f
                    chipStrokeColor = android.content.res.ColorStateList.valueOf(selectedStrokeColor)

                    // Yazı boyutunu biraz arttır ve yeniden ölçüm/yerleşim tetikle
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, selectedTextSizeSp)
                    post {
                        requestLayout()
                        group.requestLayout()
                        group.invalidate()
                    }
                }
            } else {
                binding.edtFreeText.isEnabled = true
            }
        }
    }

    // BU FONKSİYON EKLENDİ
    @ColorInt
    private fun getThemeColor(@AttrRes attrRes: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }
}