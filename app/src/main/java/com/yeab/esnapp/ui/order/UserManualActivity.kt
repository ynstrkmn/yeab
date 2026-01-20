// `app/src/main/java/your/package/UserManualActivity.kt`
package com.yeab.esnapp.ui.order

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowCompat
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityUserManualBinding
import com.yeab.esnapp.ui.base.BaseActivity


class UserManualActivity : BaseActivity() {

    private lateinit var binding: ActivityUserManualBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityUserManualBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fun setHtmlText(resId: Int) {
            binding.txtManualContent.text = HtmlCompat.fromHtml(
                getString(resId),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        }

        // Varsayılan: TR
        setHtmlText(R.string.user_manual_tr)

        binding.btnFlagTR.setOnClickListener { setHtmlText(R.string.user_manual_tr) }
        binding.btnFlagEN.setOnClickListener { setHtmlText(R.string.user_manual_en) }
    }
}
