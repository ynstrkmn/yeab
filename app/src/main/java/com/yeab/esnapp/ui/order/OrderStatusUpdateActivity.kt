package com.yeab.esnapp.ui.order

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.chip.Chip
import com.google.firebase.database.*
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.storage.FirebaseStorage
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityOrderStatusUpdateBinding
import com.yeab.esnapp.databinding.ItemProductStatusBinding
import com.yeab.esnapp.model.MerchantMessageTemplate
import com.yeab.esnapp.model.MerchantUser
import com.yeab.esnapp.model.Order
import com.yeab.esnapp.model.ProductStatus
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.util.DateFormats
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys
import com.yeab.esnapp.util.WhatsAppUtils
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.text.format
import kotlin.text.get
import kotlin.toString
import com.yeab.esnapp.util.FileUtils
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.launch
import java.io.File

class OrderStatusUpdateActivity : BaseActivity() {

    private lateinit var binding: ActivityOrderStatusUpdateBinding
    private val dbRef = FirebaseDatabase.getInstance().reference

    private var merchantUid: String? = null
    private lateinit var phone: String
    private lateinit var orderId: String

    private lateinit var statusAdapter: ProductStatusAdapter

    private var customerNameSurname: String = ""
    private var productName: String = ""
    private val templateMap = mutableMapOf<Int, MerchantMessageTemplate>()

    private lateinit var imgOrderPhoto: ImageView
    private var capturedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityOrderStatusUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)
        phone = intent.getStringExtra(IntentKeys.PHONE) ?: ""
        orderId = intent.getStringExtra(IntentKeys.ORDER_ID) ?: ""

        binding.btnUpdate.text = getString(R.string.message_template_button_update_order)
        binding.txtCustomerName.text =
            getString(R.string.order_status_customer_placeholder)

        // ChipGroup: tek seçim
        binding.radioGroupTemplates.isSingleSelection = true

        statusAdapter = ProductStatusAdapter()
        binding.recyclerStatusHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerStatusHistory.adapter = statusAdapter

        showLoading()
        loadTemplates()
        loadOrderDetails()
        loadCustomerInfo()

        val btnCamera = findViewById<Button>(R.id.btnCameraStatus)
        imgOrderPhoto = findViewById(R.id.imgOrderPhotoStatus)
        val uid = merchantUid
        if (!uid.isNullOrEmpty() && orderId.isNotEmpty()) {
            loadExistingOrderPhoto(uid, orderId)
        }
        btnCamera.setOnClickListener {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                photoUri = createImageUri(this)
                takePicture.launch(photoUri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnUpdate.setOnClickListener {
            updateOrderStatus()
        }

        setupChipGroupListener();

    }

    private fun loadExistingOrderPhoto(merchantUid: String, orderId: String) {
        val storageRef = FirebaseStorage.getInstance()
            .reference.child("ordersPhoto/$merchantUid/$orderId")
        storageRef.listAll()
            .addOnSuccessListener { listResult ->
                if (listResult.items.isNotEmpty()) {
                    // İstersen son ekleneni almak için sıralayabilirsin; burada ilk öğe alınıyor.
                    val photoRef = listResult.items.first()
                    showLoading()
                    photoRef.downloadUrl
                        .addOnSuccessListener { uri ->
                            imgOrderPhoto.apply {
                                visibility = ImageView.VISIBLE
                            }
                            hideLoading()
                            Glide.with(this)
                                .load(uri)
                                .centerCrop()
                                .placeholder(android.R.drawable.ic_menu_report_image)
                                .error(android.R.drawable.ic_menu_report_image)
                                .into(imgOrderPhoto)
                        }
                }
            }
    }

    // Kamera izni sonucu
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                photoUri = createImageUri(this)
                takePicture.launch(photoUri)
            } else {
                Toast.makeText(this, getString(R.string.error_camera_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }

    private fun loadTemplates() {
        val uid = merchantUid ?: return

        dbRef.child(FirebasePaths.MERCHANT_MESSAGE_TEMPLATES)
            .child(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                // Listener'ı geçici olarak kaldır, çipler eklenirken tetiklenmesin
                binding.radioGroupTemplates.setOnCheckedStateChangeListener(null)
                binding.radioGroupTemplates.removeAllViews()
                templateMap.clear()

                for (child in snapshot.children) {
                    val template = child.getValue(MerchantMessageTemplate::class.java) ?: continue
                    val text = template.Text ?: continue

                    val chip = Chip(this).apply {
                        id = View.generateViewId()
                        this.text = text
                        isCheckable = true
                        isClickable = true
                        isCheckedIconVisible = false
                    }

                    binding.radioGroupTemplates.addView(chip)
                    templateMap[chip.id] = template
                }

                // Çipler eklendikten sonra listener'ı tekrar kur.
                setupChipGroupListener()
                // Başlangıçta tüm stilleri sıfırla.
                resetAllChipStyles()
            }
    }

    // YARDIMCI FONKSİYON: Tüm çipleri varsayılan stiline döndürür.
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

    // ANA LISTENER FONKSİYONU
    private fun setupChipGroupListener() {
        val selectedColor = ContextCompat.getColor(this, R.color.chip_selected_background)
        val selectedStrokeColor = ContextCompat.getColor(this, R.color.chip_selected_stroke)

        binding.radioGroupTemplates.setOnCheckedStateChangeListener { group, checkedIds ->
            // Önce tüm çiplerin stilini sıfırla
            resetAllChipStyles()

            if (checkedIds.isNotEmpty()) {
                binding.edtFreeText.isEnabled = false
                // 1. BİR ÇİP SEÇİLDİ
                val selectedChipId = checkedIds.first()
                val selectedChip = group.findViewById<Chip>(selectedChipId)

                if (selectedChip != null) {
                    // a) Seçilen çipin stilini YEŞİL yap
                    selectedChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(selectedColor)
                    selectedChip.chipStrokeWidth = 4f // Çerçeveyi belirgin yap
                    selectedChip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(selectedStrokeColor))

                }

            } else {
                // EditText'i tekrar aktif hale getir
                binding.edtFreeText.isEnabled=true
            }
        }
    }

    // Bu yardımcı fonksiyonu da sınıfınıza ekleyin (eğer yoksa)
    @ColorInt
    private fun getThemeColor(@AttrRes attrRes: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    /**
     * Order detayını (ProductName, ProductImageUrl, ProductStatus) yükler ve ekrana basar.
     */
    private fun loadOrderDetails() {
        val uid = merchantUid ?: return

        val merchantOrderRef = dbRef.child(FirebasePaths.ORDERS_ROOT)
            .child(FirebasePaths.ORDERS_MERCHANT_ORDERS)
            .child(uid)
            .child(phone)
            .child(orderId)

        merchantOrderRef.get().addOnSuccessListener { snapshot ->

            val order = snapshot.getValue(Order::class.java)
            if (order == null) {
                hideLoading()
                Toast.makeText(
                    this,
                    getString(R.string.error_no_records_found),
                    Toast.LENGTH_SHORT
                ).show()
                return@addOnSuccessListener
            }

            // Ödeme durumunu ayarla
            binding.chkPaymentDone.isChecked = order.isPaymentDone

            // Eğer giriş aşamasında veya önceden ödeme yapıldıysan karıştırma olmaması için disable edildi.
            if(binding.chkPaymentDone.isChecked){
                binding.chkPaymentDone.isEnabled = false
            }

            // Ürün adı: Intent'ten geldiyse onu kullan, yoksa DB'dekini
            val productNameFromIntent = intent.getStringExtra(IntentKeys.PRODUCT_NAME)
            val finalProductName = productNameFromIntent
                ?: order.productName
                ?: ""

            productName = finalProductName

            if (finalProductName.isNotEmpty()) {
                binding.txtMatchedProduct.text =
                    getString(R.string.order_status_matched_product, finalProductName)
            } else {
                binding.txtMatchedProduct.text =
                    getString(R.string.order_status_matched_product_placeholder)
            }

            // Ürün görseli: ProductImageUrl doluysa thumbnail'e yükle
            val imageUrl = order.productImageUrl
            if (!imageUrl.isNullOrEmpty()) {
                loadProductImage(imageUrl)
            } else {
                // Görsel yoksa global loading'i kapat
                hideLoading()
            }

            // Durum geçmişi: ProductStatus listesini adapter'a ver
            val statusList = order.productStatus ?: emptyList<ProductStatus>()
            if (statusList.isEmpty()) {
                Toast.makeText(
                    this,
                    getString(R.string.order_status_history_empty),
                    Toast.LENGTH_SHORT
                ).show()
            }
            statusAdapter.submitList(statusList)
        }.addOnFailureListener {
            hideLoading()
            Toast.makeText(
                this,
                it.message ?: getString(R.string.error_generic),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun loadProductImage(url: String) {
        binding.imgLoading.visibility = View.VISIBLE

        Glide.with(this)
            .load(url)
            .centerCrop()
            .apply( RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH))
            .placeholder(android.R.drawable.ic_menu_report_image)
            .error(android.R.drawable.ic_menu_report_image)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.imgLoading.visibility = View.GONE
                    hideLoading()
                    return false // Glide'in kendi error handling'i de çalışsın
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.imgLoading.visibility = View.GONE
                    hideLoading()
                    return false
                }
            })
            .into(binding.imgProductThumbnail)
    }

    /**
     * MerchantsUsers/{merchantUid}/{phone} altından müşteri ad/soyad bilgisini çeker.
     */
    private fun loadCustomerInfo() {
        val uid = merchantUid ?: return
        if (phone.isEmpty()) return

        dbRef.child(FirebasePaths.MERCHANTS_USERS)
            .child(uid)
            .child(phone)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val user = snapshot.getValue(MerchantUser::class.java)
                    val name = user?.Name ?: ""
                    val surname = user?.Surname ?: ""

                    if (name.isNotEmpty() || surname.isNotEmpty()) {
                        customerNameSurname = "$name $surname"
                        binding.txtCustomerName.text =
                            getString(R.string.order_status_customer_label, name, surname)
                    }
                }
            }
    }

    // kotlin
    private fun updateOrderStatus() {
        val uid = merchantUid ?: return

        val checkedId = binding.radioGroupTemplates.checkedChipId
        val selectedTemplateText = if (checkedId != View.NO_ID) {
            val chip = binding.radioGroupTemplates.findViewById<Chip>(checkedId)
            chip?.text?.toString()
        } else null

        val freeText = binding.edtFreeText.text.toString().trim()

        val messageText = when {
            !selectedTemplateText.isNullOrEmpty() -> selectedTemplateText
            freeText.isNotEmpty() -> freeText
            else -> {
                Toast.makeText(
                    this,
                    getString(R.string.error_no_message_selected),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        val now = Date()
        val isoFormatter = SimpleDateFormat(DateFormats.ORDER_STATUS_ISO, Locale.getDefault())
        val nowIso = isoFormatter.format(now)
        val newStatus = ProductStatus(messageText, nowIso)

        val merchantOrderRef = dbRef.child(FirebasePaths.ORDERS_ROOT)
            .child(FirebasePaths.ORDERS_MERCHANT_ORDERS)
            .child(uid)
            .child(phone)
            .child(orderId)

        showLoading()

        merchantOrderRef.get().addOnSuccessListener { snapshot ->
            val order = snapshot.getValue(Order::class.java)
            if (order == null) {
                hideLoading()
                Toast.makeText(
                    this,
                    getString(R.string.error_no_records_found),
                    Toast.LENGTH_SHORT
                ).show()
                return@addOnSuccessListener
            }

            // Status listesine yeni status ekle
            val existing = order.productStatus
            val newList = mutableListOf<ProductStatus>()
            if (existing != null) {
                newList.addAll(existing)
            }
            newList.add(newStatus)
            order.productStatus = newList

            // Ödeme durumunu güncelle
            order.isPaymentDone = binding.chkPaymentDone.isChecked

            // CreatedDate yoksa set et
            if (order.createdDate.isNullOrEmpty()) {
                order.createdDate = nowIso
            }

            // \[FIX] WhatsApp öncesi ana kayıtları güncelle (path API'lerini kullanmadan)
            val userOrderRef = dbRef.child(FirebasePaths.USER_ORDERS_ROOT)
                .child(phone).child(uid).child(orderId)

            val baseUpdates = hashMapOf<String, Any?>(
                "${FirebasePaths.ORDERS_ROOT}/${FirebasePaths.ORDERS_MERCHANT_ORDERS}/$uid/$phone/$orderId" to order,
                "${FirebasePaths.USER_ORDERS_ROOT}/$phone/$uid/$orderId" to order
            )

            // chkOperationDone işaretliyse CompletedOrders node'larına taşı
            val moveToCompleted = binding.chkOperationDone.isChecked;
            if (moveToCompleted) {
                // JSON yapısına uygun ek alanlar
                order.isFinished = true
                order.phoneNumber = phone

                // DateOrders için YYYYMMDD
                val dateKey = try {
                    val parser = SimpleDateFormat(DateFormats.ORDER_STATUS_ISO, Locale.getDefault())
                    val created = parser.parse(order.createdDate!!)
                    val ymd = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                    ymd.format(created!!)
                } catch (_: Exception) {
                    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now)
                }

                baseUpdates["CompletedOrders/MerchantOrders/PhoneOrders/$uid/$phone/$orderId"] = order
                baseUpdates["CompletedOrders/MerchantOrders/DateOrders/$uid/$dateKey/$orderId"] = order
                baseUpdates["CompletedOrders/UserOrders/$phone/$uid/$orderId"] = order
                baseUpdates["${FirebasePaths.ORDERS_ROOT}/${FirebasePaths.ORDERS_MERCHANT_ORDERS}/$uid/$phone/$orderId"] = null
                baseUpdates["${FirebasePaths.USER_ORDERS_ROOT}/$phone/$uid/$orderId"] = null

                // Image_hashes -> CompletedImageHashes taşıması: önce oku sonra updateChildren içinde işle
                val imageHashRef = dbRef.child("image_hashes").child(uid).child(orderId)
                imageHashRef.get().addOnSuccessListener { imgSnap ->
                    if (imgSnap.exists()) {
                        // Mevcut veriyi CompletedImageHashes altına ekle ve eskiyi null yap
                        baseUpdates["CompletedImageHashes/$uid/$orderId"] = imgSnap.value
                        baseUpdates["image_hashes/$uid/$orderId"] = null
                        dbRef.updateChildren(baseUpdates).addOnSuccessListener {

                            uploadCapturedPhotoIfAny(uid, orderId)

                            // WhatsApp mesajı hazırlığı
                            val displayDate = try {
                                val createdIso = order.createdDate
                                if (!createdIso.isNullOrEmpty()) {
                                    val parser =
                                        SimpleDateFormat(DateFormats.ORDER_STATUS_ISO, Locale.getDefault())
                                    val createdDate = parser.parse(createdIso)
                                    val displayFormatter = SimpleDateFormat(
                                        DateFormats.ORDER_STATUS_DISPLAY,
                                        Locale.getDefault()
                                    )
                                    displayFormatter.format(createdDate!!)
                                } else {
                                    val displayFormatter = SimpleDateFormat(
                                        DateFormats.ORDER_STATUS_DISPLAY,
                                        Locale.getDefault()
                                    )
                                    displayFormatter.format(now)
                                }
                            } catch (e: Exception) {
                                val displayFormatter =
                                    SimpleDateFormat(DateFormats.ORDER_STATUS_DISPLAY, Locale.getDefault())
                                displayFormatter.format(now)
                            }

                            val customerDisplayName =
                                if (customerNameSurname.isNotEmpty()) customerNameSurname else phone
                            val safeProductName = if (productName.isNotEmpty()) {
                                productName
                            } else {
                                order.productName ?: getString(R.string.app_name)
                            }
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
                            val detailLink =
                                "https://esnaf.online/index.html?merchantId=$uid&orderId=$orderId&orderDate=$dateKey"

                            val formattedMessage = getString(
                                R.string.whatsapp_status_message,
                                customerDisplayName,
                                displayDate,
                                orderId,
                                safeProductName,
                                messageText,
                                detailLink,
                                "asdadasdasdsad"
                            )

                            hideLoading()
                            WhatsAppUtils.sendMessage(this, phone, formattedMessage)
                            finish()
                        }.addOnFailureListener {
                            hideLoading()
                            Toast.makeText(
                                this,
                                it.message ?: getString(R.string.error_generic),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            else {

                dbRef.updateChildren(baseUpdates).addOnSuccessListener {

                    uploadCapturedPhotoIfAny(uid, orderId)

                    // WhatsApp mesajı hazırlığı
                    val displayDate = try {
                        val createdIso = order.createdDate
                        if (!createdIso.isNullOrEmpty()) {
                            val parser =
                                SimpleDateFormat(DateFormats.ORDER_STATUS_ISO, Locale.getDefault())
                            val createdDate = parser.parse(createdIso)
                            val displayFormatter = SimpleDateFormat(
                                DateFormats.ORDER_STATUS_DISPLAY,
                                Locale.getDefault()
                            )
                            displayFormatter.format(createdDate!!)
                        } else {
                            val displayFormatter = SimpleDateFormat(
                                DateFormats.ORDER_STATUS_DISPLAY,
                                Locale.getDefault()
                            )
                            displayFormatter.format(now)
                        }
                    } catch (e: Exception) {
                        val displayFormatter =
                            SimpleDateFormat(DateFormats.ORDER_STATUS_DISPLAY, Locale.getDefault())
                        displayFormatter.format(now)
                    }

                    val customerDisplayName =
                        if (customerNameSurname.isNotEmpty()) customerNameSurname else phone
                    val safeProductName = if (productName.isNotEmpty()) {
                        productName
                    } else {
                        order.productName ?: getString(R.string.app_name)
                    }
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
                    val detailLink =
                        "https://esnaf.online/index.html?merchantId=$uid&orderId=$orderId&orderDate=$dateKey"

                    val formattedMessage = getString(
                        R.string.whatsapp_status_message,
                        customerDisplayName,
                        displayDate,
                        orderId,
                        safeProductName,
                        messageText,
                        detailLink,
                        "asdadasdasdsad"
                    )

                    hideLoading()
                    WhatsAppUtils.sendMessage(this, phone, formattedMessage)
                    finish()
                }.addOnFailureListener {
                    hideLoading()
                    Toast.makeText(
                        this,
                        it.message ?: getString(R.string.error_generic),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.addOnFailureListener {
            hideLoading()
            Toast.makeText(
                this,
                it.message ?: getString(R.string.error_generic),
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    // --- Status history adapter ---

    class ProductStatusAdapter :
        androidx.recyclerview.widget.RecyclerView.Adapter<ProductStatusAdapter.StatusViewHolder>() {

        private val items = mutableListOf<ProductStatus>()

        fun submitList(list: List<ProductStatus>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        class StatusViewHolder(val binding: ItemProductStatusBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(
            parent: android.view.ViewGroup,
            viewType: Int
        ): StatusViewHolder {
            val inflater = android.view.LayoutInflater.from(parent.context)
            val binding =
                ItemProductStatusBinding.inflate(inflater, parent, false)
            return StatusViewHolder(binding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
            val item = items[position]
            holder.binding.txtStatus.text = item.status
            holder.binding.txtDate.text = item.date
        }
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
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
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

    // Kotlin
    private fun uploadCapturedPhotoIfAny(
        merchantUid: String,
        orderId: String
    ) {
        val bmp = capturedBitmap ?: return
        val storage = FirebaseStorage.getInstance()
        val dirRef = storage.reference.child("ordersPhoto/$merchantUid/$orderId")


        // Önce mevcut fotoğrafları sil
        dirRef.listAll()
            .addOnSuccessListener { listResult ->
                val deletions = listResult.items.map { it.delete() }
                // Tüm silmeler tamamlandığında yeni fotoğrafı yükle
                com.google.android.gms.tasks.Tasks.whenAllComplete(deletions)
                    .addOnSuccessListener {
                        val baos = java.io.ByteArrayOutputStream()
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
                        customCompressImageFromCamera(FileUtils.from(this, uri), orderId)
                    }
                    .addOnFailureListener {
                        // Silme başarısızsa yine de yeni fotoğrafı yüklemeyi deneyebilirsin
                        val baos = java.io.ByteArrayOutputStream()
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
                        customCompressImageFromCamera(FileUtils.from(this, uri), orderId)
                    }
            }
            .addOnFailureListener {
                // Listeme başarısızsa direkt yüklemeye geç
                val baos = java.io.ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                val data = baos.toByteArray()

                val uri = Uri.parse(
                    MediaStore.Images.Media.insertImage(
                        contentResolver,
                        bmp,
                        "temp",
                        null
                    )
                )
                customCompressImageFromCamera(FileUtils.from(this, uri), orderId)
            }
    }

    private var compressedImage: File? = null

    private fun customCompressImageFromCamera(actualImage: File?, orderId: String) {
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
                compressedImage = Compressor.compress(this@OrderStatusUpdateActivity, imageFile) {
                    resolution(980, 1280)
                    quality(40)
                    format(Bitmap.CompressFormat.JPEG)
                    size(2_097_152) // 2 MB
                }

                val ts = System.currentTimeMillis()
                val path = "ordersPhoto/$merchantUid/$orderId/${orderId}_${ts}.jpg"

                compressedImage?.readBytes()?.let {
                    FirebaseStorage.getInstance().reference.child(path)
                        .putBytes(it)
                        .addOnSuccessListener { }
                        .addOnFailureListener { }
                }
            }
        } ?: showError("Please choose an image!")
    }

    private fun showError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

}
