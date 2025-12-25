package com.yeab.esnapp.ui.components

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
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

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.activity_phone_contact_input, this, true)

        edtPhone = findViewById(R.id.edtPhone)
        btnPickContact = findViewById(R.id.btnPickContact)

        btnPickContact.setOnClickListener {
            onPickContactClick?.invoke()
        }
    }

    // Numarayı dışarıdan almak için
    fun getPhoneNumber(): String {
        return edtPhone.text.toString().trim()
    }

    // Numarayı kod ile set etmek için
    fun setPhoneNumber(number: String) {
        edtPhone.setText(number)
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
                        var number = it.getString(numberIndex)
                        // Normalize number: Remove non-digit chars first, but be careful with +
                        // Simple cleanup: remove spaces and dashes
                        number = number.replace(" ", "").replace("-", "")
                        
                        // Remove leading 0 if present (but keep country code if logic requires, request was specifically remove leading 0)
                        // Assuming local numbers like 0532... -> 532...
                        if (number.startsWith("0")) {
                            number = number.substring(1)
                        }
                        
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