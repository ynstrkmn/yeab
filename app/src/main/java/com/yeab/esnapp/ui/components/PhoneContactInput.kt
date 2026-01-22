package com.yeab.esnapp.ui.components

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText
import com.yeab.esnapp.R

class PhoneContactInput @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val edtPhone: TextInputEditText
    private val btnPickContact: ImageButton

    // Activity'den tetiklenecek lambda fonksiyonu
    var onPickContactClick: (() -> Unit)? = null
    // Dışarıya telefon değişim geri çağrısı
    private var onPhoneChanged: ((String) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.activity_phone_contact_input, this, true)

        edtPhone = findViewById(R.id.edtPhone)
        btnPickContact = findViewById(R.id.btnPickContact)

        btnPickContact.setOnClickListener {
            onPickContactClick?.invoke()
        }

        edtPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onPhoneChanged?.invoke(s?.toString().orEmpty())
            }
        })
    }

    fun setOnPhoneChangedListener(listener: (String) -> Unit) {
        onPhoneChanged = listener
        // İlk değer gerekli ise tetikleyebilirsiniz:
        // onPhoneChanged?.invoke(getPhoneNumber())
    }

    /**
     * Numarayı temizleyip formatlayan yardımcı fonksiyon.
     * 05314444444 veya +905314444444 gibi numaraları 5314444444 formatına getirir.
     */
    private fun cleanNumber(raw: String): String {
        // Sadece rakamları al, diğer her şeyi temizle (+, -, boşluk, parantez)
        var cleaned = raw.replace(Regex("[^0-9]"), "")

        // Başındaki 90'ı veya 0'ı temizle (Türkiye formatı için: 10 hane kalsın)
        // Eğer 905xx... (12 hane) ise baştaki 90'ı at
        if (cleaned.startsWith("90") && cleaned.length >= 12) {
            cleaned = cleaned.substring(2)
        }
        // Eğer 05xx... (11 hane) ise baştaki 0'ı at
        else if (cleaned.startsWith("0") && cleaned.length >= 11) {
            cleaned = cleaned.substring(1)
        }

        // Eğer hala 10 haneden uzunsa (örn uluslararası başka kod), son 10 haneyi al
        if (cleaned.length > 10) {
            cleaned = cleaned.takeLast(10)
        }

        return cleaned
    }

    // Numarayı dışarıdan almak için
    fun getPhoneNumber(): String {
        return cleanNumber(edtPhone.text.toString())
    }

    // Numarayı kod ile set etmek için
    fun setPhoneNumber(number: String) {
        edtPhone.setText(cleanNumber(number))
    }

    /**
     * Rehberden dönen URI'yi işleyip numarayı otomatik yazar.
     */
    fun setPhoneNumberFromUri(contactUri: Uri) {
        try {
            // Sadece numara kolonunu istiyoruz
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)

            val cursor = context.contentResolver.query(contactUri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numberIndex != -1) {
                        val number = it.getString(numberIndex)
                        setPhoneNumber(number)
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Numara alınamadı", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
}
