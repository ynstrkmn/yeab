package com.yeab.esnapp.ui.order.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.firebase.database.*
import com.yeab.esnapp.databinding.DialogUserInfoCustomBinding // Oluşturduğumuz XML'in binding adı

class UserInfoDialogFragment : DialogFragment() {

    private var _binding: DialogUserInfoCustomBinding? = null
    private val binding get() = _binding!!

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

        // View Binding başlatma
        _binding = DialogUserInfoCustomBinding.inflate(layoutInflater)

        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)

        val dialog = builder.create()

        // Köşelerin yuvarlak görünmesi için arka planı şeffaf yapıyoruz
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        binding.btnClose.setOnClickListener { dialog.dismiss() }

        // Firebase Veri Çekme
        val ref = FirebaseDatabase.getInstance()
            .getReference("MerchantsUsers")
            .child(merchantUID)
            .child(phone)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return

                val name = snapshot.child("Name").getValue(String::class.java)
                    ?: snapshot.child("name").getValue(String::class.java) ?: "-"
                val surname = snapshot.child("Surname").getValue(String::class.java)
                    ?: snapshot.child("surname").getValue(String::class.java) ?: "-"
                val mobile = snapshot.child("MobilePhoneNumber").getValue(Any::class.java)?.toString() ?: phone

                // UI Güncelleme (item_merchant_order tarzında)
                binding.txtUserName.text = "$name $surname"
                binding.txtUserPhone.text = "Tel: $mobile"
                binding.loadingBar.visibility = View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding == null) return
                binding.txtUserName.text = "Hata oluştu"
                binding.loadingBar.visibility = View.GONE
            }
        })

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}