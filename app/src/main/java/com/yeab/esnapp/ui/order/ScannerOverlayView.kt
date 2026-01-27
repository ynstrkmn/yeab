package com.yeab.esnapp.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) // Delik açıcı
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1ABC9C") // YEŞİL ÇERÇEVE
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 1. Ekranı yarı saydam siyah yap
        canvas.drawColor(Color.parseColor("#99000000"))

        // 2. Ortadaki kareyi hesapla (Genişliğin %70'i, Yüksekliğin %20'si gibi)
        val width = width.toFloat()
        val height = height.toFloat()

        // Kutunun boyutları (AutoCaptureActivity'deki crop mantığıyla uyumlu olsun)
        val boxWidth = width * 0.70f
        val boxHeight = height * 0.25f

        val cx = width / 2f
        val cy = height / 2f

        rect.set(
            cx - (boxWidth / 2),
            cy - (boxHeight / 2),
            cx + (boxWidth / 2),
            cy + (boxHeight / 2)
        )

        // 3. Deliği aç ve çerçeveyi çiz
        setLayerType(LAYER_TYPE_HARDWARE, null)
        canvas.drawRoundRect(rect, 30f, 30f, eraser) // Delik
        canvas.drawRoundRect(rect, 30f, 30f, borderPaint) // Yeşil Çizgi
    }
}