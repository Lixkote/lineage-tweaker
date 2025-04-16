package com.drdisagree.iconify.ui.fragments.xposed

import com.drdisagree.iconify.R
import com.drdisagree.iconify.ui.base.ControlledPreferenceFragmentCompat

class QsMargins : ControlledPreferenceFragmentCompat() {

    override val title: String
        get() = getString(R.string.custom_qs_margin_title)

    override val backButtonEnabled: Boolean
        get() = true

    override val layoutResource: Int
        get() = R.xml.xposed_qs_margins

    override val hasMenu: Boolean
        get() = true
}