package com.yeab.esnapp.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityAuthChoiceBinding
import com.yeab.esnapp.ui.home.HomeActivity
import com.yeab.esnapp.util.FirebasePaths
import com.yeab.esnapp.util.IntentKeys

class AuthChoiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthChoiceBinding
    private lateinit var auth: FirebaseAuth
    private var mode: String = "login"

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            if (task.isSuccessful) {
                val account = task.result
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { authResult ->
                    if (authResult.isSuccessful) {
                        val uid = auth.currentUser?.uid ?: return@addOnCompleteListener

                        // Login veya register sonrası push token’ı Merchant kaydına yaz
                        updatePushToken(uid)

                        if (mode == "register") {
                            val intent = Intent(this, MerchantInfoActivity::class.java)
                            intent.putExtra(IntentKeys.MERCHANT_UID, uid)
                            startActivity(intent)
                        } else {
                            val intent = Intent(this, HomeActivity::class.java)
                            intent.putExtra(IntentKeys.MERCHANT_UID, uid)
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        Log.e("AuthChoice", "Firebase auth failed", authResult.exception)
                    }
                }
            } else {
                Log.e("AuthChoice", "Google sign in failed", task.exception)
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
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val client = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(client.signInIntent)
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
