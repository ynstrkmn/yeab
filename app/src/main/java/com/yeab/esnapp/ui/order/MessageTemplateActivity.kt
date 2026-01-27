package com.yeab.esnapp.ui.order

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
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
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.yeab.esnapp.util.FileUtils
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.launch
import java.io.File
import android.content.Context

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
    private var productImageUrl: String? = null
    private var productAdditionalmageUrl: String? = null
    private var isPaymentDone: Boolean = false
    private var paymentDate: String = ""

    // Yeni eklenenler: NewOrder'dan gelen ham veriler
    private var localPhotoUriStr: String? = null
    private var recognizedText: String = ""

    private var customerNameSurname: String = ""
    private val templateMap = mutableMapOf<Int, MerchantMessageTemplate>()
    private var orderIdOrigin: String = ""
    private lateinit var imgOrderPhoto: ImageView
    private var capturedBitmap: Bitmap? = null
    private var orderNumber: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMessageTemplateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)
        phone = intent.getStringExtra(IntentKeys.PHONE) ?: ""
        name = intent.getStringExtra(IntentKeys.NAME) ?: ""
        surname = intent.getStringExtra(IntentKeys.SURNAME) ?: ""
        email = intent.getStringExtra(IntentKeys.EMAIL) ?: ""
        productDesc = intent.getStringExtra(IntentKeys.PRODUCT_DESC) ?: ""
        isPaymentDone = intent.getBooleanExtra(IntentKeys.IS_PAYMENT_DONE, false)

        // Artık URL yerine yerel URI ve Text geliyor
        productImageUrl = intent.getStringExtra(IntentKeys.PRODUCT_IMAGE_URL) // Varsa (edit modunda vs)
        localPhotoUriStr = intent.getStringExtra("extra_local_photo_uri")
        recognizedText = intent.getStringExtra("extra_recognized_text") ?: ""

        orderIdOrigin = intent.getStringExtra(IntentKeys.ORDER_ID) ?: ""
        orderNumber = intent.getStringExtra(IntentKeys.ORDER_NUMBER) ?: ""
        // Eğer ID yoksa (yeni sipariş) burada oluşturuyoruz
        if (orderIdOrigin.isEmpty()) {
            orderIdOrigin = System.currentTimeMillis().toString()
        }

        customerNameSurname = listOf(name, surname)
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        val btnCamera = findViewById<Button>(R.id.btnCamera)
        imgOrderPhoto = findViewById(R.id.imgOrderPhoto)
        btnCamera.setOnClickListener {
            photoUri = createImageUri(this)
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            takePicture.launch(cameraIntent)
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


    private fun loadTemplates() {
        val uid = merchantUid ?: return
        showLoading()
        dbRef.child(FirebasePaths.MERCHANT_MESSAGE_TEMPLATES)
            .child(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    hideLoading()
                    binding.radioGroupTemplates.setOnCheckedStateChangeListener(null)
                    binding.radioGroupTemplates.removeAllViews()
                    templateMap.clear()

                    if(snapshot.exists()) binding.btnMessageTemplates.visibility = LinearLayout.GONE
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

                override fun onCancelled(error: DatabaseError) {
                    hideLoading()
                }
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

    @ColorInt
    private fun getThemeColor(@AttrRes attrRes: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

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

        // EKLEME: Sipariş kaydından önce OrderNumber doğrulaması
        verifyOrderNumberThenProcess(uid, messageText)
    }

    // EKLEME: OrderNumber doğrulama
    private fun verifyOrderNumberThenProcess(uid: String, messageText: String) {
        showLoading()
        dbRef.child(FirebasePaths.MERCHANTS)
            .child(uid)
            .child("OrderNumber")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val value = snapshot.value
                    val currentDbOrderNumber = when (value) {
                        is String -> value
                        is Number -> value.toLong().toString()
                        else -> ""
                    }

                    if (currentDbOrderNumber.isEmpty()) {
                        hideLoading()
                        Toast.makeText(this@MessageTemplateActivity, "OrderNumber bulunamadı", Toast.LENGTH_SHORT).show()
                        return
                    }

                    if (currentDbOrderNumber == orderNumber) {
                        // Eşleşti, mevcut akışa devam
                        if (localPhotoUriStr != null) {
                            uploadImageAndProcess(uid, Uri.parse(localPhotoUriStr!!), messageText)
                        } else {
                            processOrderSave(uid, messageText)
                        }
                    } else {
                        hideLoading()
                        Toast.makeText(this@MessageTemplateActivity, "Ürün numarası güncel değil. Lütfen yeniden deneyin.", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    hideLoading()
                    Toast.makeText(this@MessageTemplateActivity, error.message, Toast.LENGTH_SHORT).show()
                }
            })
    }

    // --- UPLOAD VE HASH MANTIĞI (NewOrder'dan taşındı) ---

    private fun uploadImageAndProcess(uid: String, uri: Uri, messageText: String) {
        showLoading()

        // Bitmap'i oluştur
        val bitmap = try {
            val inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }

        if (bitmap == null) {
            hideLoading()
            Toast.makeText(this, "Resim işlenemedi", Toast.LENGTH_SHORT).show()
            return
        }

        val imageId = UUID.randomUUID().toString()
        val fileName = "orders/$uid/$imageId.jpg"
        val imgRef = storageRef.child(fileName)
        val refOrderId = orderIdOrigin

        // EXIF düzeltme
        val correctedBitmap = try {
            fixBitmapOrientation(bitmap, uri)
        } catch (e: Exception) {
            bitmap
        }

        val baos = ByteArrayOutputStream()
        correctedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val data = baos.toByteArray()

        val sha256 = try { sha256Hex(data) } catch (e: Exception) { "" }
        val phash = try { averageHash(correctedBitmap) } catch (e: Exception) { "" }

        imgRef.putBytes(data)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                imgRef.downloadUrl
            }
            .addOnSuccessListener { downloadUrl ->
                // URL'i güncelle
                productImageUrl = downloadUrl.toString()

                val meta = HashMap<String, Any?>()
                meta["imageUrl"] = productImageUrl
                meta["hash"] = sha256
                meta["phash"] = phash
                meta["fileName"] = fileName
                meta["timestamp"] = ServerValue.TIMESTAMP
                meta["recognizedText"] = recognizedText
                meta["orderNumber"] = orderNumber

                // Hash bilgisini kaydet
                dbRef.child("image_hashes")
                    .child(uid)
                    .child(refOrderId)
                    .setValue(meta)
                    .addOnCompleteListener {
                        if (capturedBitmap != null) {

                            uploadCapturedPhotoIfAny(uid, refOrderId, messageText)
                        }
                        else {
                            // Yükleme bitti, sipariş kaydına devam et
                            processOrderSave(uid, messageText)
                        }
                    }
            }
            .addOnFailureListener {
                hideLoading()
                Toast.makeText(this, getString(R.string.error_image_upload) + ": " + it.message, Toast.LENGTH_SHORT).show()
            }
    }

    private fun processOrderSave(uid: String, messageText: String) {
        saveCustomerIfNeeded(uid) {
            saveOrderAndSendWhatsApp(uid, orderIdOrigin, messageText)
        }
    }

    // --- HELPER FUNCTIONS ---

    private fun fixBitmapOrientation(src: Bitmap, uri: android.net.Uri): Bitmap {
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
                android.graphics.Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            } ?: src
        } catch (e: Exception) {
            src
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun averageHash(src: Bitmap): String {
        val size = 8
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        val luminances = IntArray(pixels.size)
        var sum = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val lum = (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)).toInt()
            luminances[i] = lum
            sum += lum
        }
        val avg = sum / luminances.size
        val bits = StringBuilder()
        for (lum in luminances) bits.append(if (lum >= avg) '1' else '0')
        return String.format("%016x", BigInteger(bits.toString(), 2))
    }

    // --- END HELPER FUNCTIONS ---


    private fun saveCustomerIfNeeded(uid: String, onDone: () -> Unit) {
        val userRef = dbRef.child(FirebasePaths.MERCHANTS_USERS)
            .child(uid)
            .child(phone)

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

    // KÜÇÜK DÜZENLEME: Firebase internal \`path\` kullanımını kaldırıp string ile update yapıldı.
    private fun saveOrderAndSendWhatsApp(
        uid: String,
        orderId: String,
        messageText: String
    ) {
        val now = Date()
        val isoFormatter = SimpleDateFormat(DateFormats.ORDER_STATUS_ISO, Locale.getDefault())
        val nowIso = isoFormatter.format(now)

        val statusList = mutableListOf(ProductStatus(messageText, nowIso))
        paymentDate = if (isPaymentDone) nowIso else ""

        val order = Order(
            false,
            productImageUrl,
            productAdditionalmageUrl,
            productDesc,
            statusList,
            nowIso,
            isPaymentDone,
            paymentDate,
            phone,
            orderNumber
        )

        // \`path.toString()\` yerine açık string yollar
        val merchantOrderPath = "${FirebasePaths.ORDERS_ROOT}/${FirebasePaths.ORDERS_MERCHANT_ORDERS}/$uid/$phone/$orderId"
        val userOrderPath = "${FirebasePaths.USER_ORDERS_ROOT}/$phone/$uid/$orderId"

        val updates = hashMapOf<String, Any>(
            merchantOrderPath to order,
            userOrderPath to order
        )

        // DateOrders için YYYYMMDD
        val dateKey = try {
            val parser =
                SimpleDateFormat(DateFormats.ORDER_STATUS_ISO, Locale.getDefault())
            val created = parser.parse(order.createdDate!!)
            val ymd = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            ymd.format(created!!)
        } catch (_: Exception) {
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now)
        }

        dbRef.updateChildren(updates).addOnSuccessListener {
            // EKLEME: Kaydetme sonrası OrderNumber'ı +1 olarak güncelle
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

    // EKLEME: OrderNumber'ı atomik olarak 1 arttır
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
    // Kamera için preview contract
    private val takePicturePreview = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            capturedBitmap = bitmap
            imgOrderPhoto.apply {
                setImageBitmap(bitmap)
                visibility = ImageView.VISIBLE
            }
        }
    }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK  ) {
                // photoUri → TAM ÇÖZÜNÜRLÜKLÜ
                capturedBitmap = photoUri.let { uri ->
                    val inputStream = contentResolver.openInputStream(uri)
                    android.graphics.BitmapFactory.decodeStream(inputStream)
                }
                imgOrderPhoto.apply {
                    setImageBitmap(photoUri.let { uri ->
                        val inputStream = contentResolver.openInputStream(uri)
                        android.graphics.BitmapFactory.decodeStream(inputStream)
                    })
                    visibility = ImageView.VISIBLE
                }
            }
        }

    private lateinit var photoUri: Uri

    fun createImageUri(context: Context): Uri {
        val imageFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "photo_${System.currentTimeMillis()}.jpg"
        )

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }
    private fun uploadCapturedPhotoIfAny(
        merchantUid: String,
        orderId: String,
        messageText: String
    ) {
        val bmp = capturedBitmap ?: return;
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val data = baos.toByteArray()

        val uri = Uri.parse(
            MediaStore.Images.Media.insertImage(
                contentResolver,
                bmp,
                "temp",
                null
            )
        )
        customCompressImageFromCamera(FileUtils.from(this, uri), orderId, merchantUid, messageText)
    }

    private var compressedImage: File? = null

    private fun customCompressImageFromCamera(actualImage: File?, orderId: String, uid: String, messageText: String) {
        actualImage?.let { imageFile ->
            lifecycleScope.launch {
                // Default compression with custom destination file
                /*compressedImage = Compressor.compress(this@MainActivity, imageFile) {
                    default()
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.also {
                        val file = File("${it.absolutePath}${File.separator}my_image.${imageFile.extension}")
                        destination(file)
                    }
                }*/

                // Full custom
                compressedImage = Compressor.compress(this@MessageTemplateActivity, imageFile) {
                    resolution(980, 1280)
                    quality(40)
                    format(Bitmap.CompressFormat.JPEG)
                    size(2_097_152) // 2 MB
                }

                val ts = System.currentTimeMillis()
                val path = "ordersPhoto/$merchantUid/$orderId/${orderId}_${ts}.jpg"
                val imgRef = FirebaseStorage.getInstance().reference.child(path)

                compressedImage?.readBytes()?.let {
                    imgRef.putBytes(it)
                        .continueWithTask { task ->
                            if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                            imgRef.downloadUrl
                        }
                        .addOnSuccessListener { downloadUrl ->
                            // URL'i güncelle
                            productAdditionalmageUrl = downloadUrl.toString()

                            processOrderSave(uid, messageText)

                        }
                        .addOnFailureListener { }
                }
            }
        } ?: showError("Please choose an image!")
    }

    private fun showError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }
}