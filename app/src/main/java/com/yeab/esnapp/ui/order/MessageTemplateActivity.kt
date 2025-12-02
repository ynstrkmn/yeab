package com.yeab.esnapp.ui.order

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    private var customerNameSurname: String = ""
    private val templateMap = mutableMapOf<Int, MerchantMessageTemplate>()


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
        productImageUrl = intent.getStringExtra(IntentKeys.PRODUCT_IMAGE_URL)

        customerNameSurname = listOf(name, surname)
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        binding.txtTitle.text = getString(R.string.message_template_title)
        binding.btnSaveOrder.text = getString(R.string.message_template_button_update_order)
        binding.edtFreeText.hint = getString(R.string.message_template_hint_free_text)

        loadTemplates()

        binding.btnSaveOrder.setOnClickListener {
            onCompleteClicked()
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
                    // Template1, Template2, Template3...
                    val template = child.getValue(MerchantMessageTemplate::class.java) ?: continue
                    val text = template.Text ?: continue

                    val radio = RadioButton(this)
                    radio.text = text

                    // ID verip map'e koyuyoruz ki gerekirse Finish/Start flag'lerine erişebilelim
                    val id = View.generateViewId()
                    radio.id = id

                    binding.radioGroupTemplates.addView(radio)
                    templateMap[id] = template
                }
            }
    }


    private fun onCompleteClicked() {
        val uid = merchantUid
        if (uid.isNullOrEmpty() || phone.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
            return
        }

        val selectedId = binding.radioGroupTemplates.checkedRadioButtonId
        val selectedTemplate = if (selectedId != -1) {
            findViewById<RadioButton>(selectedId).text.toString()
        } else null

        val freeText = binding.edtFreeText.text.toString().trim()

        val messageText = when {
            !selectedTemplate.isNullOrEmpty() -> selectedTemplate
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
            val orderId = System.currentTimeMillis().toString()
            saveOrderAndSendWhatsApp(uid, orderId, messageText)
        }
    }

    /**
     * MerchantsUsers/{uid}/{phone} altında kayıt yoksa müşteri kaydını oluşturur.
     */
    private fun saveCustomerIfNeeded(uid: String, onDone: () -> Unit) {
        val userRef = dbRef.child(FirebasePaths.MERCHANTS_USERS)
            .child(uid)
            .child(phone)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val user = MerchantUser()
                    user.MobilePhoneNumber = phone.toLongOrNull() ?: 0L
                    user.Name = name
                    user.Surname = surname
                    user.Email = email

                    userRef.setValue(user).addOnCompleteListener {
                        onDone()
                    }
                } else {
                    onDone()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onDone()
            }
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

        val initialStatus = ProductStatus(messageText, nowIso)

        val statusList = mutableListOf<ProductStatus>()
        statusList.add(initialStatus)

        val order = Order(
            false,              // isFinished
            productImageUrl,    // productImageUrl
            productDesc,        // productName
            statusList,         // productStatus
            nowIso              // createdDate
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
            val displayFormatter = SimpleDateFormat(
                DateFormats.ORDER_STATUS_DISPLAY,
                Locale.getDefault()
            )
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
                customerDisplayName,   // %1$s
                displayDate,           // %2$s
                orderId,               // %3$s (artık sadece millis string)
                safeProductName,       // %4$s
                messageText,           // %5$s
                detailLink             // %6$s
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
