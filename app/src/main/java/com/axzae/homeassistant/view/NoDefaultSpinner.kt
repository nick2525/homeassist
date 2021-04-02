package com.axzae.homeassistant.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.SpinnerAdapter
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSpinner
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class NoDefaultSpinner @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatSpinner(context, attrs, defStyleAttr) {

    var originalAdapter: SpinnerAdapter? = null
        private set

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        try {
            val selectedPosition = selectedItemPosition
            if (selectedPosition == -1) {
                val view = adapter.getView(-1, null, this)
                view.measure(measuredWidth, measuredHeight)
                val height = Math.max(measuredHeight, view.measuredHeight + paddingBottom + paddingTop)
                setMeasuredDimension(measuredWidth, height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun setAdapter(orig: SpinnerAdapter) {
        originalAdapter = orig
        val adapter = newProxy(orig)
        super.setAdapter(adapter)
        try {
            val m =
                AdapterView::class.java.getDeclaredMethod("setNextSelectedPositionInt", Int::class.javaPrimitiveType)
            m.isAccessible = true
            m.invoke(this, -1)
            val n = AdapterView::class.java.getDeclaredMethod("setSelectedPositionInt", Int::class.javaPrimitiveType)
            n.isAccessible = true
            n.invoke(this, -1)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    protected fun newProxy(obj: SpinnerAdapter): SpinnerAdapter {
        return Proxy.newProxyInstance(
            obj.javaClass.classLoader, arrayOf<Class<*>>(SpinnerAdapter::class.java),
            SpinnerAdapterProxy(obj)
        ) as SpinnerAdapter
    }

    /**
     * Intercepts getView() to display the prompt if position < 0
     */
    protected inner class SpinnerAdapterProxy(protected var obj: SpinnerAdapter) : InvocationHandler {
        protected var getView: Method? = null

        @Throws(Throwable::class)
        override fun invoke(proxy: Any, m: Method, args: Array<Any>): Any {
            return try {
                if (m == getView &&
                    (args[0] as Int) < 0) getView(args[0] as Int, args[1] as View, args[2] as ViewGroup) else m.invoke(
                    obj, *args
                )
            } catch (e: InvocationTargetException) {
                throw e.targetException
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        @Throws(IllegalAccessException::class)
        protected fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            if (position < 0) {
                Log.d("YouQi", "Negative Position")

                //int resIdTextView = android.R.layout.simple_spinner_item;
                //int resIdTextView = R.layout.spinner_edittext_lookalike;
                //final TextView v =  (TextView) ((LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(resIdTextView, parent, false);
                val v = obj.getView(0, convertView, parent) as TextView
                v.setTextColor(v.currentHintTextColor) //ContextCompat.getColor(getContext(), R.color.md_grey_300));
                v.textSize = 18f
                v.text = prompt
                //v.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                return v
            }
            return obj.getView(position, convertView, parent)
        }

        init {
            try {
                getView = SpinnerAdapter::class.java.getMethod(
                    "getView", Int::class.javaPrimitiveType, View::class.java, ViewGroup::class.java
                )
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }
}