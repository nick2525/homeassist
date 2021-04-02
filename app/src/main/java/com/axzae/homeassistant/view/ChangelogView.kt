package com.axzae.homeassistant.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.axzae.homeassistant.R

class ChangelogView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        gravity = Gravity.CENTER_VERTICAL
        orientation = VERTICAL
    }

    fun loadLogs(answers: List<String?>) {
        Log.d("YouQi", "loadLogs called")
        val inflater = LayoutInflater.from(context)
        removeAllViews()
        for (answer in answers) {
            val row = inflater.inflate(R.layout.item_changelog_row, this, false)
            val rowView = row.findViewById<View>(R.id.text_log) as TextView
            rowView.text = answer
            addView(row)
        }
    }
}