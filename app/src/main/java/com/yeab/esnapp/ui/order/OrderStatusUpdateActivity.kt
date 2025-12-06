package com.yeab.esnapp.ui.order

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.chip.Chip
import com.google.firebase.database.*
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        binding.btnUpdate.setOnClickListener {
            updateOrderStatus()
        }
    }

    private fun loadTemplates() {
        val uid = merchantUid ?: return

        dbRef.child(FirebasePaths.MERCHANT_MESSAGE_TEMPLATES)
            .child(uid)
            .get()
            .addOnSuccessListener { snapshot ->
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
            }
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

            // CreatedDate yoksa bir defaya mahsus set et (geri uyumluluk)
            if (order.createdDate.isNullOrEmpty()) {
                order.createdDate = nowIso
            }

            val userOrderRef = dbRef.child(FirebasePaths.USER_ORDERS_ROOT)
                .child(phone).child(uid).child(orderId)

            val updates = hashMapOf<String, Any>(
                merchantOrderRef.path.toString().substring(1) to order,
                userOrderRef.path.toString().substring(1) to order
            )

            dbRef.updateChildren(updates).addOnSuccessListener {

                // 1) CreatedDate'i görüntülenecek formata çevir
                val displayDate = try {
                    val createdIso = order.createdDate
                    if (!createdIso.isNullOrEmpty()) {
                        val parser = SimpleDateFormat(
                            DateFormats.ORDER_STATUS_ISO,
                            Locale.getDefault()
                        )
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
                    val displayFormatter = SimpleDateFormat(
                        DateFormats.ORDER_STATUS_DISPLAY,
                        Locale.getDefault()
                    )
                    displayFormatter.format(now)
                }

                // 2) Müşteri adı yoksa fallback telefon
                val customerDisplayName =
                    if (customerNameSurname.isNotEmpty()) customerNameSurname else phone

                // 3) ProductName yoksa order.productName veya app_name
                val safeProductName =
                    if (productName.isNotEmpty()) {
                        productName
                    } else {
                        order.productName ?: getString(R.string.app_name)
                    }

                // 4) Detay linki
                val detailLink =
                    "https://esnapp-qr.web.app/index.html?merchantId=$uid&orderId=$orderId"

                // 5) Locale'e göre TR/EN şablon
                val formattedMessage = getString(
                    R.string.whatsapp_status_message,
                    customerDisplayName,   // %1$s
                    displayDate,           // %2$s
                    orderId,               // %3$s
                    safeProductName,       // %4$s
                    messageText,           // %5$s
                    detailLink             // %6$s
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
}
