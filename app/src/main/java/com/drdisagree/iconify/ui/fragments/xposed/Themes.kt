package com.drdisagree.iconify.ui.fragments.xposed

import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.common.Preferences.BLACK_QSPANEL
import com.drdisagree.iconify.data.common.Preferences.FLUID_NOTIF_TRANSPARENCY
import com.drdisagree.iconify.data.common.Preferences.FLUID_POWERMENU_TRANSPARENCY
import com.drdisagree.iconify.data.common.Preferences.FLUID_QSPANEL
import com.drdisagree.iconify.data.common.Preferences.LIGHT_QSPANEL
import com.drdisagree.iconify.ui.activities.MainActivity
import com.drdisagree.iconify.ui.base.ControlledPreferenceFragmentCompat

class Themes : ControlledPreferenceFragmentCompat() {

    override val title: String
        get() = getString(R.string.activity_title_themes)

    override val backButtonEnabled: Boolean
        get() = true

    override val layoutResource: Int
        get() = R.xml.xposed_themes

    override val hasMenu: Boolean
        get() = true

    override fun updateScreen(key: String?) {
        super.updateScreen(key)

        when (key) {
            LIGHT_QSPANEL,
            BLACK_QSPANEL,
            FLUID_QSPANEL,
            FLUID_NOTIF_TRANSPARENCY,
            FLUID_POWERMENU_TRANSPARENCY -> {
                MainActivity.showOrHidePendingActionButton(
                    activityBinding = (requireActivity() as MainActivity).binding,
                    requiresSystemUiRestart = true
                )
            }
        }
    }
}
