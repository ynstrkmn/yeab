package com.yeab.esnapp.ui.order

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.firebase.database.*
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityMessageTemplateBinding
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

class MessageTemplateActivity : BaseActivity() {

    private lateinit var binding: ActivityMessageTemplateBinding
    private val dbRef = FirebaseDatabase.getInstance().reference

    private var merchantUid: String? = null
    private lateinit var phone: String
    private var name: String = ""
    private var surname: String = ""
    private var email: String = ""
    private var productDesc: String = ""
    private var productImageUrl: String? = null
    private var isPaymentDone: Boolean = false
    private var paymentDate: String = ""

    private var customerNameSurname: String = ""
    private val templateMap = mutableMapOf<Int, MerchantMessageTemplate>()
    private var orderIdOrigin: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageTemplateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        merchantUid = intent.getStringExtra(IntentKeys.MERCHANT_UID)
        phone = intent.getStringExtra(IntentKeys.PHONE) ?: ""
        name = intent.getStringExtra(IntentKeys.NAME) ?: ""
        surname = intent.getStringExtra(IntentKeys.SURNAME) ?: ""
        email = intent.getStringExtra(IntentKeys.EMAIL) ?: ""
        productDesc = intent.getStringExtra(IntentKeys.PRODUCT_DESC) ?: ""
        isPaymentDone = intent.getBooleanExtra(IntentKeys.IS_PAYMENT_DONE,false)
        productImageUrl = intent.getStringExtra(IntentKeys.PRODUCT_IMAGE_URL)
        orderIdOrigin = intent.getStringExtra(IntentKeys.ORDER_ID) ?: ""

        customerNameSurname = listOf(name, surname)
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        binding.txtTitle.text = getString(R.string.message_template_title)
        binding.btnSaveOrder.text = getString(R.string.message_template_button_save_order)
        binding.edtFreeText.hint = getString(R.string.message_template_hint_free_text)

        loadTemplates()

        binding.btnSaveOrder.setOnClickListener {
            onCompleteClicked()
        }

        setupChipGroupListener();

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
                        // Başlangıç stilini de burada ayarlayabiliriz.
                        // (Bu kısım setupChipGroupListener içinde de yönetilecek)
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

        for (i in 0 until binding.radioGroupTemplates.childCount) {
            val view = binding.radioGroupTemplates.getChildAt(i)
            if (view is Chip) {
                view.chipBackgroundColor = colorStateList
                view.chipStrokeWidth = 0f
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
                // 1. BİR ÇİP SEÇİLDİ
                val selectedChipId = checkedIds.first()
                val selectedChip = group.findViewById<Chip>(selectedChipId)

                if (selectedChip != null) {
                    // a) Seçilen çipin stilini YEŞİL yap
                    selectedChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(selectedColor)
                    selectedChip.chipStrokeWidth = 4f // Çerçeveyi belirgin yap
                    selectedChip.chipStrokeColor = android.content.res.ColorStateList.valueOf(selectedStrokeColor)

                }

            } else {
                // 2. SEÇİM KALDIRILDI
                // EditText'i tekrar aktif hale getir
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
                Toast.makeText(
                    this,
                    getString(R.string.error_no_message_selected),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        // Önce MerchantsUsers altında müşteri kaydı yoksa ekleyelim
        saveCustomerIfNeeded(uid) {
            // 🔴 BURASI ÖNEMLİ: OrderId artık sadece System.currentTimeMillis()
            val orderId = orderIdOrigin
            saveOrderAndSendWhatsApp(uid, orderId, messageText)
        }
    }

    /**
     * MerchantsUsers/{uid}/{phone} altında müşteri kaydı yoksa ekler.
     */
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

    /**
     * Yeni Order kaydını hem MerchantOrders hem UserOrders altına yazar
     * ve zengin WhatsApp mesajını gönderir.
     */
    private fun saveOrderAndSendWhatsApp(
        uid: String,
        orderId: String,
        messageText: String
    ) {
        val now = Date()
        val isoFormatter = SimpleDateFormat(DateFormats.ORDER_STATUS_ISO, Locale.getDefault())
        val nowIso = isoFormatter.format(now)

        val statusList = mutableListOf(ProductStatus(messageText, nowIso))

        // Eğer ödeme yapıldısa ödeme olarak şuan tarih atılmalıdır.

        if (isPaymentDone){
            paymentDate = nowIso
        }else{
            paymentDate = ""
        }

        val order = Order(
            false,              // isFinished
            productImageUrl,    // productImageUrl
            productDesc,        // productName
            statusList,         // productStatus
            nowIso,              // createdDate
            isPaymentDone,
            paymentDate
        )

        val merchantOrderRef = dbRef.child(FirebasePaths.ORDERS_ROOT)
            .child(FirebasePaths.ORDERS_MERCHANT_ORDERS)
            .child(uid)
            .child(phone)
            .child(orderId)

        val userOrderRef = dbRef.child(FirebasePaths.USER_ORDERS_ROOT)
            .child(phone)
            .child(uid)
            .child(orderId)

        val updates = hashMapOf<String, Any>(
            merchantOrderRef.path.toString().substring(1) to order,
            userOrderRef.path.toString().substring(1) to order
        )

        dbRef.updateChildren(updates).addOnSuccessListener {

            // Tarihi display formatına çevir
            val displayFormatter =
                SimpleDateFormat(DateFormats.ORDER_STATUS_DISPLAY, Locale.getDefault())
            val displayDate = displayFormatter.format(now)

            // Müşteri adı yoksa telefon göster
            val customerDisplayName =
                if (customerNameSurname.isNotEmpty()) customerNameSurname else phone

            val safeProductName =
                if (productDesc.isNotEmpty()) productDesc else getString(R.string.app_name)

            val detailLink =
                "https://esnapp-qr.web.app/index.html?merchantId=$uid&orderId=$orderId"

            val formattedMessage = getString(
                R.string.whatsapp_status_message,
                customerDisplayName,
                displayDate,
                orderId,
                safeProductName,
                messageText,
                detailLink
            )

            WhatsAppUtils.sendMessage(this, phone, formattedMessage)
            finish()

        }.addOnFailureListener {
            Toast.makeText(
                this,
                it.message ?: getString(R.string.error_generic),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
