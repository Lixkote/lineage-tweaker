package com.drdisagree.iconify.ui.fragments.xposed

import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.common.Preferences.DUAL_STATUSBAR
import com.drdisagree.iconify.ui.activities.MainActivity
import com.drdisagree.iconify.ui.base.ControlledPreferenceFragmentCompat

class DualStatusbar : ControlledPreferenceFragmentCompat() {

    override val title: String
        get() = getString(R.string.activity_title_dual_statusbar)

    override val backButtonEnabled: Boolean
        get() = true

    override val layoutResource: Int
        get() = R.xml.xposed_dual_statusbar

    override val hasMenu: Boolean
        get() = true

    override fun updateScreen(key: String?) {
        super.updateScreen(key)

        when (key) {
            DUAL_STATUSBAR -> {
                MainActivity.showOrHidePendingActionButton(
                    activityBinding = (requireActivity() as MainActivity).binding,
                    requiresSystemUiRestart = true
                )
            }
        }
    }
}
