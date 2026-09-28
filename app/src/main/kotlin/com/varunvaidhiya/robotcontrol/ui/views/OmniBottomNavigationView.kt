package com.varunvaidhiya.robotcontrol.ui.views

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * BottomNavigationView allows at most five menu items. This app has six
 * primary destinations, so the limit is raised to match the menu.
 */
class OmniBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.bottomNavigationStyle
) : BottomNavigationView(context, attrs, defStyleAttr) {
    override fun getMaxItemCount(): Int = 8
}
