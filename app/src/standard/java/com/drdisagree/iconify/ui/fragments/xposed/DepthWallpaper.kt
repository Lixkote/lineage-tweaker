package com.drdisagree.iconify.ui.fragments.xposed

import com.drdisagree.iconify.R
import com.drdisagree.iconify.ui.preferences.PreferenceMenu
import com.drdisagree.iconify.xposed.modules.extras.utils.BitmapSubjectSegmenter
import com.google.android.gms.common.moduleinstall.ModuleAvailabilityResponse

class DepthWallpaper : BaseDepthWallpaper() {

    override fun PreferenceMenu.setMLKitStatus() {
        BitmapSubjectSegmenter(requireContext())
            .checkModelAvailability { moduleAvailabilityResponse: ModuleAvailabilityResponse? ->
                setSummary(
                    getString(
                        if (moduleAvailabilityResponse?.areModulesAvailable() == true) {
                            R.string.depth_wallpaper_model_ready
                        } else {
                            R.string.depth_wallpaper_model_not_available
                        }
                    )
                )
            }
    }
}
