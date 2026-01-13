package com.yeab.esnapp.ui.splash

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.yeab.esnapp.databinding.ActivitySplashBinding
import com.yeab.esnapp.ui.auth.MerchantLoginActivity
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.ui.home.HomeActivity
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys
import com.yeab.esnapp.util.NetworkUtils

class SplashActivity : BaseActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val REQ_CODE_UPDATE = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!NetworkUtils.isInternetAvailable(this)) {
            // İnternet yoksa hemen uyar ve ilerlemesine izin verme
            showNoInternetDialog()
            return
        }
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkForUpdate(this as Activity)
    }

    fun checkForUpdate(activity: Activity) {
        val rc = FirebaseRemoteConfig.getInstance()
        val configSettings = remoteConfigSettings { minimumFetchIntervalInSeconds = 1 }
        rc.setConfigSettingsAsync(configSettings)

        rc.fetchAndActivate()
            .addOnSuccessListener {
                val minVersion = rc.getLong("min_version_code").toInt()
                val force = rc.getBoolean("force_update")
                val msg = rc.getString("update_message")

                if (getLongVersionCode(activity) < minVersion) {
                    if (force) {
                        showForceUpdateDialog(activity, msg)
                    } else {
                        showOptionalUpdateDialog(activity, msg)
                    }
                } else {
                    Handler(Looper.getMainLooper()).post({
                        navigateNext()
                    })
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(activity, "Güncelleme kontrolünde hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    fun getLongVersionCode(context: Context): Long {
        val pm = context.packageManager
        val pkg = context.packageName
        return try {
            val pi = pm.getPackageInfo(pkg, 0)
            PackageInfoCompat.getLongVersionCode(pi)
        } catch (e: Exception) {
            0L
        }
    }

    private fun navigateNext() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val dbRef = FirebaseDatabase.getInstance().reference
            dbRef.child(FirebasePaths.MERCHANTS)
                .child(currentUser.uid).addListenerForSingleValueEvent(object: ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            // Kullanıcı login, direkt ana sayfaya
                            val intent = Intent(applicationContext, HomeActivity::class.java)
                            intent.putExtra(IntentKeys.MERCHANT_UID, currentUser.uid)
                            startActivity(intent)
                        } else {
                            // Login ekranına git
                            val intent = Intent(applicationContext, MerchantLoginActivity::class.java)
                            startActivity(intent)
                        }
                        finish()
                    }

                    override fun onCancelled(error: DatabaseError) {

                    }

                } )
        } else {
            // Login ekranına git
            val intent = Intent(this, MerchantLoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun showForceUpdateDialog(activity: Activity, message: String) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Güncelleme gerekli")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Güncelle") { _, _ ->
                tryStartInAppUpdate(activity)
            }
            // Cancel butonu koymak istemezsek eklemeyin; zorunluysa kapatılamaz olmalı
            .create()
        dialog.show()
    }

    private fun showOptionalUpdateDialog(activity: Activity, message: String) {
        AlertDialog.Builder(activity)
            .setTitle("Güncelleme var")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Güncelle") { _, _ -> tryStartInAppUpdate(activity) }
            .setNegativeButton("Sonra") { d, _ ->
                Handler(Looper.getMainLooper()).post({
                    d.dismiss()
                    navigateNext()
                })
            }
            .show()
    }

    private fun tryStartInAppUpdate(activity: Activity) {
        val appUpdateManager = AppUpdateManagerFactory.create(activity)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        AppUpdateType.IMMEDIATE,
                        activity,
                        REQ_CODE_UPDATE
                    )
                } catch (e: Exception) {
                    // Başlatılamadıysa Play Store'a yönlendir
                    openPlayStore(activity)
                    finish()
                }
            } else {
                openPlayStore(activity)
                finish()
            }
        }.addOnFailureListener {
            openPlayStore(activity)
            finish()
        }
    }

    private fun openPlayStore(activity: Activity) {
        val packageName = activity.packageName
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
            activity.startActivity(intent)
        }
    }

}
