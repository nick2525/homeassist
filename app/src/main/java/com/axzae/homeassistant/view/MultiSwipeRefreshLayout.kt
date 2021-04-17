package com.axzae.homeassistant.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener

/**
 * https://stackoverflow.com/questions/31226658/swiperefreshlayout-interferes-with-scrollview-in-a-viewpager
 *
 *
 * A descendant of [SwipeRefreshLayout] which supports multiple
 * child views triggering a refresh gesture. You set the views which can trigger the gesture via
 * [.setSwipeableChildren], providing it the child ids.
 */
class MultiSwipeRefreshLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SwipeRefreshLayout(context, attrs) {
    private lateinit var mSwipeableChildren: Array<View?>
    var isIdle = true

    /**
     * Set the children which can trigger a refresh by swiping down when they are visible. These
     * views need to be a descendant of this view.
     */
    fun setSwipeableChildren(vararg ids: Int) {
        assert(ids != null)

        // Iterate through the ids and find the Views
        mSwipeableChildren = arrayOfNulls(ids.size)
        for (i in 0 until ids.size) {
            mSwipeableChildren[i] = findViewById(ids[i])
        }
    }

    fun setSwipeableChildren(mViewPager: ViewPager) {
        mViewPager.addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                //Log.d("YouQi", "onPageSelected" + position);
            }

            override fun onPageScrollStateChanged(state: Int) {
                isIdle = state == ViewPager.SCROLL_STATE_IDLE
                //ViewPager.SCROLL_STATE_IDLE
                //ViewPager.SCROLL_STATE_DRAGGING
                //ViewPager.SCROLL_STATE_SETTLING
                //Log.d("YouQi", "onPageScrollStateChanged" + state);
            }
        })
    }
    // BEGIN_INCLUDE(can_child_scroll_up)
    /**
     * This method controls when the swipe-to-refresh gesture is triggered. By returning false here
     * we are signifying that the view is in a state where a refresh gesture can start.
     *
     *
     *
     * As [SwipeRefreshLayout] only supports one direct child by
     * default, we need to manually iterate through our swipeable children to see if any are in a
     * state to trigger the gesture. If so we return false to start the gesture.
     */
    override fun canChildScrollUp(): Boolean {
        //Log.d("YouQi", "canChildScrollUp? " + (mSwipeableChildren == null ? "null" : mSwipeableChildren.length));
        //        if (mSwipeableChildren != null && mSwipeableChildren.length > 0) {
        //            // Iterate through the scrollable children and check if any of them can not scroll up
        //            for (View view : mSwipeableChildren) {
        //                if (view != null && view instanceof ViewPager) {
        //                    ViewPager mViewPager = (ViewPager) view;
        //                    // Log.d("YouQi", "fake? " + (mViewPager.isFakeDragging() ? "true" : "false"));
        //                }
        //
        //                if (view != null && view.isShown() && !canViewScrollUp(view)) {
        //                    // If the view is shown, and can not scroll upwards, return false and start the
        //                    // gesture.
        //                    return false;
        //                }
        //            }
        //        }
        return !isIdle
    }

    companion object {
        // END_INCLUDE(can_child_scroll_up)
        // BEGIN_INCLUDE(can_view_scroll_up)
        /**
         * Utility method to check whether a [View] can scroll up from it's current position.
         * Handles platform version differences, providing backwards compatible functionality where
         * needed.
         */
        private fun canViewScrollUp(view: View): Boolean {
            // For ICS and above we can call canScrollVertically() to determine this
            //return ViewCompat.canScrollVertically(view, -1);

            // Log.d("YouQi", "vert?" + (view.canScrollVertically(-1) ? "true" : "false"));
            return view.canScrollVertically(-1)
        } // END_INCLUDE(can_view_scroll_up)
    }
}