package com.yeab.esnapp.ui.order

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowCompat
import com.yeab.esnapp.R
import com.yeab.esnapp.databinding.ActivityUserManualBinding
import com.yeab.esnapp.ui.base.BaseActivity

class UserManualActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_manual)

        val webView = findViewById<WebView>(R.id.webManual)
        webView.settings.defaultTextEncodingName = "utf-8"

        // 'app/src/main/res/values/strings.xml' ve 'values-en/strings.xml' içinde html içerik: user_manual_content_html
        val rawHtml = getString(R.string.user_manual_content)

        // Başlıkları belirginleştiren ve kenarlarda boşluk veren CSS
        val styledHtml = """
            <!DOCTYPE html>
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <style>
                  body { font-family: sans-serif; color:#000; padding:16px; line-height:1.5; }
                  h1, h2, h3 { font-weight:700; color:#000; margin-top:12px; margin-bottom:8px; }
                  h1 { font-size:20px; letter-spacing:0.02em; }
                  h2 { font-size:18px; }
                  h3 { font-size:16px; }
                  p, li { font-size:20px; }
                  ul, ol { padding-left:20px; }
                </style>
              </head>
              <body>
                $rawHtml
              </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null)
    }
}