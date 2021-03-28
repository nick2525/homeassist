package com.axzae.homeassistant.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.axzae.homeassistant.R

class FixedAspectRatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        init(context, attrs)
    }

    private var mAspectRatioWidth = 0
    private var mAspectRatioHeight = 0

    private fun init(context: Context, attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.FixedAspectRatioFrameLayout)
        mAspectRatioWidth = a.getInt(R.styleable.FixedAspectRatioFrameLayout_aspectRatioWidth, 4)
        mAspectRatioHeight = a.getInt(R.styleable.FixedAspectRatioFrameLayout_aspectRatioHeight, 3)
        a.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val originalWidth = MeasureSpec.getSize(widthMeasureSpec)
        val originalHeight = MeasureSpec.getSize(heightMeasureSpec)
        val calculatedHeight = originalWidth * mAspectRatioHeight / mAspectRatioWidth
        val finalWidth: Int = originalWidth
        val finalHeight: Int = calculatedHeight
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(finalWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY)
        )
    }
}