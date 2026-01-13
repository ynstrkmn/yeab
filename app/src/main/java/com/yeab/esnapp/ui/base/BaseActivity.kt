package com.yeab.esnapp.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yeab.esnapp.R
import com.yeab.esnapp.util.NetworkUtils

open class BaseActivity : AppCompatActivity() {

    private var loadingDialog: AlertDialog? = null

    protected fun showLoading(message: String? = null) {
        if (isFinishing) return

        // Zaten gösteriliyorsa yeniden oluşturma
        if (loadingDialog?.isShowing == true) {
            // Mesajı güncellemek istersek:
            val tv = loadingDialog?.findViewById<TextView>(R.id.txtLoadingMessage)
            if (message != null && tv != null) {
                tv.text = message
            }
            return
        }

        val builder = AlertDialog.Builder(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null, false)
        val tvMessage = view.findViewById<TextView>(R.id.txtLoadingMessage)
        tvMessage.text = message ?: getString(R.string.loading_default)

        builder.setView(view)
        builder.setCancelable(false)

        loadingDialog = builder.create()
        loadingDialog?.show()
    }

    override fun onResume() {
        super.onResume()
        if (!NetworkUtils.isInternetAvailable(this)) {
            showNoInternetDialog()
        }
    }

    public fun showNoInternetDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("İnternet Bağlantısı Yok")
            .setMessage("Lütfen internet bağlantınızı kontrol edin ve tekrar deneyin.")
            .setCancelable(false) // Kullanıcı kapatamasın
            .setPositiveButton("Tekrar Dene") { _, _ ->
                if (!NetworkUtils.isInternetAvailable(this)) {
                    showNoInternetDialog() // Hala yoksa tekrar göster
                }
            }
            .setNegativeButton("Kapat") { _, _ ->
                finishAffinity() // Uygulamayı tamamen kapat
            }
            .show()
    }

    protected fun hideLoading() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    override fun onDestroy() {
        hideLoading()
        super.onDestroy()
    }
}
