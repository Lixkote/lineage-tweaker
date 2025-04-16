package com.drdisagree.iconify.ui.fragments.xposed

import android.os.Build
import android.os.Bundle
import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.common.Preferences.HIDE_LOCKSCREEN_CARRIER
import com.drdisagree.iconify.data.common.Preferences.HIDE_LOCKSCREEN_LOCK_ICON
import com.drdisagree.iconify.data.common.Preferences.HIDE_LOCKSCREEN_STATUSBAR
import com.drdisagree.iconify.data.common.Preferences.HIDE_QS_ON_LOCKSCREEN
import com.drdisagree.iconify.data.common.Preferences.LOCKSCREEN_WALLPAPER_BLUR
import com.drdisagree.iconify.data.common.Preferences.LOCKSCREEN_WALLPAPER_BLUR_RADIUS
import com.drdisagree.iconify.ui.activities.MainActivity
import com.drdisagree.iconify.ui.base.ControlledPreferenceFragmentCompat
import com.drdisagree.iconify.ui.preferences.SwitchPreference

class Lockscreen : ControlledPreferenceFragmentCompat() {

    override val title: String
        get() = getString(R.string.activity_title_lockscreen)

    override val backButtonEnabled: Boolean
        get() = true

    override val layoutResource: Int
        get() = R.xml.xposed_lockscreen

    override val hasMenu: Boolean
        get() = true

    override fun updateScreen(key: String?) {
        super.updateScreen(key)

        when (key) {
            HIDE_LOCKSCREEN_LOCK_ICON,
            HIDE_LOCKSCREEN_CARRIER,
            HIDE_LOCKSCREEN_STATUSBAR,
            LOCKSCREEN_WALLPAPER_BLUR,
            LOCKSCREEN_WALLPAPER_BLUR_RADIUS -> {
                MainActivity.showOrHidePendingActionButton(
                    activityBinding = (requireActivity() as MainActivity).binding,
                    requiresSystemUiRestart = true
                )
            }

            HIDE_QS_ON_LOCKSCREEN -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    MainActivity.showOrHidePendingActionButton(
                        activityBinding = (requireActivity() as MainActivity).binding,
                        requiresSystemUiRestart = true
                    )
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        findPreference<SwitchPreference>(HIDE_QS_ON_LOCKSCREEN)?.apply {
            title = getString(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) R.string.hide_qs_on_lockscreen_a15_title
                else R.string.hide_qs_on_lockscreen_title
            )
            summary = getString(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) R.string.hide_qs_on_lockscreen_a15_desc
                else R.string.hide_qs_on_lockscreen_desc
            )
        }
    }
}
