package com.example.b1void.adapters

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter

// Stubbed class to fix build
class ImagePagerAdapter(private val imagePaths: List<String>) : PagerAdapter() {
    override fun getCount(): Int {
        return 0
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view == `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        return super.instantiateItem(container, position)
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        // Empty
    }
}