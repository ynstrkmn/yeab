package com.yeab.esnapp.ui.messages

import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityMerchantMessageTemplatesBinding
import com.yeab.esnapp.model.MessageTemplate
import com.yeab.esnapp.ui.base.BaseActivity

class MerchantMessageTemplatesActivity : BaseActivity() {

    private lateinit var binding: ActivityMerchantMessageTemplatesBinding
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMerchantMessageTemplatesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val merchantUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database = FirebaseDatabase.getInstance()
            .getReference("MerchantMessageTemplates")
            .child(merchantUid)

        binding.btnAddTemplates.setOnClickListener {
            saveTemplates()
        }

    }

    private fun saveTemplates() {
        val templates = mutableMapOf<String, MessageTemplate>()

        val container = binding.root.findViewById<LinearLayout>(R.id.rootLayout)
        var templateIndex = 1

        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i)
            val etMessage = view.findViewById<EditText>(R.id.etMessage)
            val cbFinish = view.findViewById<CheckBox>(R.id.cbFinish)

            val text = etMessage?.text?.toString()?.trim() ?: ""

            if (text.isNotEmpty()) {
                templates["Template$templateIndex"] = MessageTemplate(
                    false,
                    Finish = cbFinish.isChecked,
                    Text = text
                )
                templateIndex++
            }
        }

        if (templates.isEmpty()) {
            Toast.makeText(this, "En az bir mesaj giriniz", Toast.LENGTH_SHORT).show()
            return
        }

        database.setValue(templates)
            .addOnSuccessListener {
                Toast.makeText(this, "Şablonlar kaydedildi", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Hata oluştu", Toast.LENGTH_SHORT).show()
            }
    }
}
