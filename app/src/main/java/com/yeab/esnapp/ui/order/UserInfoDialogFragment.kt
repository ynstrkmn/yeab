// kotlin
package com.yeab.esnapp.ui.order.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.firebase.database.*

class UserInfoDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_MERCHANT_UID = "ARG_MERCHANT_UID"
        private const val ARG_PHONE = "ARG_PHONE"

        fun newInstance(merchantUID: String, phone: String): UserInfoDialogFragment {
            val f = UserInfoDialogFragment()
            val args = Bundle()
            args.putString(ARG_MERCHANT_UID, merchantUID)
            args.putString(ARG_PHONE, phone)
            f.arguments = args
            return f
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val merchantUID = requireArguments().getString(ARG_MERCHANT_UID)!!
        val phone = requireArguments().getString(ARG_PHONE)!!

        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Kullanıcı Bilgisi")
            .setMessage("Yükleniyor...")
            .setPositiveButton("Kapat", null)

        val dialog = builder.create()

        val ref = FirebaseDatabase.getInstance()
            .getReference("MerchantsUsers")
            .child(merchantUID)
            .child(phone)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.child("Name").getValue(String::class.java)
                    ?: snapshot.child("name").getValue(String::class.java) ?: "-"
                val surname = snapshot.child("Surname").getValue(String::class.java)
                    ?: snapshot.child("surname").getValue(String::class.java) ?: "-"
                val mobile = snapshot.child("MobilePhoneNumber").getValue(Any::class.java)?.toString() ?: phone
                dialog.setMessage("Ad: $name\nSoyad: $surname\nTelefon: $mobile")
            }
            override fun onCancelled(error: DatabaseError) {
                dialog.setMessage("Bilgi alınamadı: ${error.message}")
            }
        })

        return dialog
    }
}
