package com.drdisagree.iconify.ui.fragments.xposed

import com.drdisagree.iconify.R
import com.drdisagree.iconify.ui.preferences.PreferenceMenu

class DepthWallpaper : BaseDepthWallpaper() {

    override fun PreferenceMenu.setMLKitStatus() {
        setSummary(
            getString(
                R.string.depth_wallpaper_model_not_available
            )
        )
    }
}
