package com.yeab.esnapp.util

import com.yeab.esnapp.model.Merchant

object MerchantSession {
    var merchant: Merchant? = null
    var merchantUid: String? = null

    fun clear() {
        merchant = null
        merchantUid = null
    }
}
