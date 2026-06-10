package com.uno.game.ui.game

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.uno.game.models.UnoCard

class UnoCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var card: UnoCard? = null

    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintEllipse = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 50
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.WHITE
    }
    private val paintShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 0, 0)
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    fun setCard(card: UnoCard) {
        this.card = card
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val card = this.card ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 24f

        // Shadow
        canvas.drawRoundRect(4f, 6f, w - 2f, h - 2f, r, r, paintShadow)

        // Background gradient
        paintBg.shader = getCardGradient(card, w, h)
        canvas.drawRoundRect(0f, 0f, w, h, r, r, paintBg)

        // Decorative ellipse in center
        val ovalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 40
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.rotate(-30f, w / 2, h / 2)
        canvas.drawOval(w * 0.1f, h * 0.15f, w * 0.9f, h * 0.85f, ovalPaint)
        canvas.restore()

        // Border
        canvas.drawRoundRect(2f, 2f, w - 2f, h - 2f, r, r, paintBorder)

        // Value text
        val displayValue = card.getDisplayValue()
        paintText.textSize = when {
            displayValue.length > 2 -> h * 0.22f
            else -> h * 0.32f
        }
        // Corner small text
        paintText.textSize = h * 0.14f
        canvas.drawText(displayValue, w * 0.18f, h * 0.22f, paintText)
        canvas.save()
        canvas.rotate(180f, w / 2, h / 2)
        canvas.drawText(displayValue, w * 0.18f, h * 0.22f, paintText)
        canvas.restore()

        // Center big text
        paintText.textSize = when {
            displayValue.length > 2 -> h * 0.28f
            else -> h * 0.38f
        }
        canvas.drawText(displayValue, w / 2, h / 2 + paintText.textSize / 3, paintText)
    }

    private fun getCardGradient(card: UnoCard, w: Float, h: Float): LinearGradient {
        return when (card.color) {
            "red"    -> LinearGradient(0f, 0f, w, h, 0xFFE53935.toInt(), 0xFFC62828.toInt(), Shader.TileMode.CLAMP)
            "green"  -> LinearGradient(0f, 0f, w, h, 0xFF43A047.toInt(), 0xFF2E7D32.toInt(), Shader.TileMode.CLAMP)
            "blue"   -> LinearGradient(0f, 0f, w, h, 0xFF1E88E5.toInt(), 0xFF1565C0.toInt(), Shader.TileMode.CLAMP)
            "yellow" -> LinearGradient(0f, 0f, w, h, 0xFFFDD835.toInt(), 0xFFF9A825.toInt(), Shader.TileMode.CLAMP)
            "wild"   -> LinearGradient(0f, 0f, w, h,
                intArrayOf(0xFFE53935.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFFFDD835.toInt()),
                floatArrayOf(0f, 0.33f, 0.66f, 1f),
                Shader.TileMode.CLAMP)
            else     -> LinearGradient(0f, 0f, w, h, 0xFF424242.toInt(), 0xFF212121.toInt(), Shader.TileMode.CLAMP)
        }
    }
}
