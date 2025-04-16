package com.drdisagree.iconify.xposed.modules.extras.views

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.reAddView

class MediaPlayerPagerAdapter(
    private val mediaPlayerViews: MutableList<Pair<String?, OpQsMediaPlayerView>>
) : PagerAdapter() {

    override fun getCount(): Int {
        return mediaPlayerViews.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = mediaPlayerViews[position].second
        val index = (view.parent as? ViewGroup)?.indexOfChild(view)?.coerceAtLeast(0) ?: 0
        container.reAddView(view, index)
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as OpQsMediaPlayerView)
    }
}