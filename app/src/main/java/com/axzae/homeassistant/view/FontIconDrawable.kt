package com.axzae.homeassistant.view

import android.R
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.TypedValue

/**
 * Embed an icon into a Drawable that can be used as TextView icons, or ActionBar icons.
 *
 *
 * new IconDrawable(context, IconValue.icon_star)
 * .colorRes(R.color.white)
 * .actionBarSize();
 *
 *
 * If you don't set the size of the drawable, it will use the size
 * that is given to him. Note that in an ActionBar, if you don't
 * set the size explicitly it uses 0, so please use actionBarSize().
 */
class FontIconDrawable(private val context: Context, private val icon: String, typeface: Typeface?) : Drawable() {
    private val paint: TextPaint = TextPaint()
    private var size = -1
    private var drawableAlpha = 255

    /**
     * Set the size of this icon to the standard Android ActionBar.
     *
     * @return The current IconDrawable for chaining.
     */
    fun actionBarSize(): FontIconDrawable {
        return sizeDp(ANDROID_ACTIONBAR_ICON_SIZE_DP)
    }

    /**
     * Set the size of the drawable.
     *
     * @param dimenRes The dimension resource.
     * @return The current IconDrawable for chaining.
     */
    fun sizeRes(dimenRes: Int): FontIconDrawable {
        return sizePx(context.resources.getDimensionPixelSize(dimenRes))
    }

    /**
     * Set the size of the drawable.
     *
     * @param size The size in density-independent pixels (dp).
     * @return The current IconDrawable for chaining.
     */
    fun sizeDp(size: Int): FontIconDrawable {
        return sizePx(dpToPx(context.resources, size))
    }

    /**
     * Set the size of the drawable.
     *
     * @param size The size in pixels (px).
     * @return The current IconDrawable for chaining.
     */
    fun sizePx(size: Int): FontIconDrawable {
        this.size = size
        setBounds(0, 0, size, size)
        invalidateSelf()
        return this
    }

    /**
     * Set the color of the drawable.
     *
     * @param color The color, usually from android.graphics.Color or 0xFF012345.
     * @return The current IconDrawable for chaining.
     */
    fun color(color: Int): FontIconDrawable {
        paint.color = color
        invalidateSelf()
        return this
    }

    /**
     * Set the color of the drawable.
     *
     * @param colorRes The color resource, from your R file.
     * @return The current IconDrawable for chaining.
     */
    fun colorRes(colorRes: Int): FontIconDrawable {
        paint.color = context.resources.getColor(colorRes)
        invalidateSelf()
        return this
    }

    /**
     * Set the alpha of this drawable.
     *
     * @param alpha The alpha, between 0 (transparent) and 255 (opaque).
     * @return The current IconDrawable for chaining.
     */
    fun alpha(alpha: Int): FontIconDrawable {
        setAlpha(alpha)
        invalidateSelf()
        return this
    }

    override fun getIntrinsicHeight(): Int {
        return size
    }

    override fun getIntrinsicWidth(): Int {
        return size
    }

    override fun draw(canvas: Canvas) {
        paint.textSize = bounds.height().toFloat()
        val textBounds = Rect()
        val textValue = icon
        paint.getTextBounds(textValue, 0, 1, textBounds)
        val textBottom = (bounds.height() - textBounds.height()) / 2f + textBounds.height() - textBounds.bottom
        canvas.drawText(textValue, bounds.width() / 2f, textBottom, paint)
    }

    override fun isStateful(): Boolean {
        return true
    }

    override fun setState(stateSet: IntArray): Boolean {
        val oldValue = paint.alpha
        val newValue = if (isEnabled(stateSet)) drawableAlpha else drawableAlpha / 2
        paint.alpha = newValue
        return oldValue != newValue
    }

    override fun setAlpha(alpha: Int) {
        this.drawableAlpha = alpha
        paint.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        paint.colorFilter = cf
    }

    override fun clearColorFilter() {
        paint.colorFilter = null
    }

    override fun getOpacity(): Int {
        return PixelFormat.OPAQUE
    }

    /**
     * Sets paint style.
     *
     * @param style to be applied
     */
    fun setStyle(style: Paint.Style?) {
        paint.style = style
    }

    companion object {
        var ANDROID_ACTIONBAR_ICON_SIZE_DP = 24

        /**
         * Dp to px.
         *
         * @param res the res
         * @param dp  the dp
         * @return the int
         */
        fun dpToPx(res: Resources, dp: Int): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
                res.displayMetrics
            ).toInt()
        }

        /**
         * Checks if is enabled.
         *
         * @param stateSet the state set
         * @return true, if is enabled
         */
        fun isEnabled(stateSet: IntArray): Boolean {
            for (state in stateSet) if (state == R.attr.state_enabled) return true
            return false
        }
    }

    /**
     * Create an IconDrawable.
     *
     * @param context Your activity or application context.
     * @param icon    The icon you want this drawable to display.
     */
    init {
        paint.typeface = typeface
        paint.style = Paint.Style.STROKE
        paint.textAlign = Paint.Align.CENTER
        paint.isUnderlineText = false
        paint.color = Color.WHITE
        paint.isAntiAlias = true
    }
}