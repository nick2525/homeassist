/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.axzae.homeassistant.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * RotationLayout rotates the contents of the layout by multiples of 90 degrees.
 *
 */
class RotationLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var mRotation = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (mRotation == 1 || mRotation == 3) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(measuredHeight, measuredWidth)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /**
     * @param degrees the rotation, in degrees.
     */
    fun setViewRotation(degrees: Int) {
        mRotation = (degrees + 360) % 360 / 90
    }

    public override fun dispatchDraw(canvas: Canvas) {
        if (mRotation == 0) {
            super.dispatchDraw(canvas)
            return
        }
        if (mRotation == 1) {
            canvas.translate(width.toFloat(), 0f)
            canvas.rotate(90f, (width / 2).toFloat(), 0f)
            canvas.translate((height / 2).toFloat(), (width / 2).toFloat())
        } else if (mRotation == 2) {
            canvas.rotate(180f, (width / 2).toFloat(), (height / 2).toFloat())
        } else {
            canvas.translate(0f, height.toFloat())
            canvas.rotate(270f, (width / 2).toFloat(), 0f)
            canvas.translate((height / 2).toFloat(), (-width / 2).toFloat())
        }
        super.dispatchDraw(canvas)
    }
}