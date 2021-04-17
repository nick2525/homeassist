package com.axzae.homeassistant.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView

class BottomNavigationViewBehavior(context: Context?, attrs: AttributeSet?) :
    CoordinatorLayout.Behavior<BottomNavigationView>(context, attrs) {
    private var oldY = 0f
    override fun layoutDependsOn(parent: CoordinatorLayout, fab: BottomNavigationView, dependency: View) =
        dependency is AppBarLayout

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: BottomNavigationView,
        dependency: View
    ): Boolean {
        if (dependency is AppBarLayout) {
            child.layoutParams as CoordinatorLayout.LayoutParams
            val dy = oldY - dependency.getY()
            moveDown(child, dy)
            oldY = dependency.getY()
        }
        return true
    }

    private fun moveDown(child: View, dy: Float) {
        var translationY = child.translationY + dy
        if (translationY < 0) {
            translationY = 0f
        }
        if (translationY > child.height) {
            translationY = child.height.toFloat()
        }
        child.translationY = translationY
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: BottomNavigationView,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int
    ) = axes == ViewCompat.SCROLL_AXIS_VERTICAL

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: BottomNavigationView,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
    ) {
        moveDown(child, dyConsumed.toFloat())
    }

}