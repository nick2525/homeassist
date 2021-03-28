package com.axzae.homeassistant.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import com.rengwuxian.materialedittext.MaterialEditText

class MaterialEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MaterialEditText(context, attrs, defStyleAttr) {

    override fun onDraw(canvas: Canvas) {
        focusFraction = 1f
        //setFocusFraction(getFloatingLabelFraction());
        super.onDraw(canvas)
    }
}