package com.yeab.esnapp.fcm

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.yeab.esnapp.util.FirebasePaths

class EsnappFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Kullanıcı login ise Merchant kaydındaki PushToken alanını güncelle
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val dbRef = FirebaseDatabase.getInstance().reference
        dbRef.child(FirebasePaths.MERCHANTS)
            .child(uid)
            .child(FirebasePaths.MERCHANT_PUSH_TOKEN_FIELD)
            .setValue(token)
    }

    // İstersen onMessageReceived içinde bildirim işleyebilirsin
    // override fun onMessageReceived(remoteMessage: RemoteMessage) { ... }
}
