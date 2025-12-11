package com.yeab.esnapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityAuthChoiceBinding
import com.yeab.esnapp.ui.base.BaseActivity
import com.yeab.esnapp.ui.home.HomeActivity
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys

class AuthChoiceActivity : BaseActivity() {

    private lateinit var binding: ActivityAuthChoiceBinding
    private lateinit var auth: FirebaseAuth
    private var mode: String = "login"

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode != RESULT_OK || data == null) {
                Log.e("AuthChoice", "Google sign in canceled or data is null")
                return@registerForActivityResult
            }

            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)

                if (account == null) {
                    Log.e("AuthChoice", "Google account is null")
                    return@registerForActivityResult
                }

                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { authResult ->
                    if (authResult.isSuccessful) {
                        val uid = auth.currentUser?.uid ?: return@addOnCompleteListener

                        val dbRef = FirebaseDatabase.getInstance().reference
                        dbRef.child(FirebasePaths.MERCHANTS)
                            .child(uid).addListenerForSingleValueEvent(object: ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.exists()) {
                                        // Login veya register sonrası push token’ı Merchant kaydına yaz
                                        updatePushToken(uid)
                                        val intent = Intent(applicationContext, HomeActivity::class.java)
                                        intent.putExtra(IntentKeys.MERCHANT_UID, uid)
                                        startActivity(intent)
                                        finish()
                                    } else {
                                        val intent = Intent(applicationContext, MerchantInfoActivity::class.java)
                                        intent.putExtra(IntentKeys.MERCHANT_UID, uid)
                                        startActivity(intent)
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {

                                }

                            } )
                    } else {
                        Log.e("AuthChoice", "Firebase auth failed", authResult.exception)
                    }
                }
            } catch (e: ApiException) {
                Log.e("AuthChoice", "Google sign in failed", e)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthChoiceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        mode = intent.getStringExtra(IntentKeys.MODE) ?: "login"

        binding.txtTitle.text = if (mode == "register") {
            getString(R.string.auth_title_register)
        } else {
            getString(R.string.auth_title_login)
        }

        binding.btnGoogle.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        // Google Sign-In konfigürasyonu
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val client = GoogleSignIn.getClient(this, gso)

        // 🔴 ÖNEMLİ: Önce signOut çağırıp cache'deki hesabı temizliyoruz ki
        // kullanıcıya her seferinde hesap seçme ekranı gelsin.
        client.signOut().addOnCompleteListener {
            // İstersek burada showLoading/hideLoading de kullanabiliriz ama
            // sadece çok kısa bir signOut olduğu için şimdilik sade bıraktım.
            googleSignInLauncher.launch(client.signInIntent)
        }
    }

    private fun updatePushToken(uid: String) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) return@addOnCompleteListener
                val token = task.result ?: return@addOnCompleteListener

                val dbRef = FirebaseDatabase.getInstance().reference
                dbRef.child(FirebasePaths.MERCHANTS)
                    .child(uid)
                    .child(FirebasePaths.MERCHANT_PUSH_TOKEN_FIELD)
                    .setValue(token)
            }
    }
}
