package com.drdisagree.iconify.ui.fragments.home

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import com.drdisagree.iconify.Iconify.Companion.appContext
import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.common.Preferences.COLOR_ACCENT_PRIMARY
import com.drdisagree.iconify.data.common.Preferences.COLOR_ACCENT_PRIMARY_LIGHT
import com.drdisagree.iconify.data.common.Preferences.COLOR_ACCENT_SECONDARY
import com.drdisagree.iconify.data.common.Preferences.COLOR_ACCENT_SECONDARY_LIGHT
import com.drdisagree.iconify.data.common.Preferences.FIRST_INSTALL
import com.drdisagree.iconify.data.common.Preferences.UPDATE_DETECTED
import com.drdisagree.iconify.data.config.RPrefs.getBoolean
import com.drdisagree.iconify.data.config.RPrefs.putBoolean
import com.drdisagree.iconify.services.UpdateScheduler.scheduleUpdates
import com.drdisagree.iconify.ui.activities.MainActivity.Companion.replaceFragment
import com.drdisagree.iconify.ui.base.ControlledPreferenceFragmentCompat
import com.drdisagree.iconify.ui.fragments.settings.AppUpdates
import com.drdisagree.iconify.ui.preferences.UpdateCheckerPreference
import com.drdisagree.iconify.utils.SystemUtils.saveBootId
import com.drdisagree.iconify.utils.SystemUtils.saveVersionCode
import com.drdisagree.iconify.utils.helper.BackupRestore.migrateToRoomDatabase
import com.drdisagree.iconify.utils.overlay.FabricatedUtils
import com.drdisagree.iconify.utils.overlay.OverlayUtils
import com.drdisagree.iconify.utils.overlay.OverlayUtils.enableOverlay
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView

class Home : ControlledPreferenceFragmentCompat(), AppBarLayout.OnOffsetChangedListener {

    private lateinit var appBarLayout: AppBarLayout

    override val title: String
        get() = getString(R.string.app_name)

    override val backButtonEnabled: Boolean
        get() = false

    override val layoutResource: Int
        get() = R.xml.home

    override val hasMenu: Boolean
        get() = false

    override val themeResource: Int
        get() = R.style.PrefsThemeCollapsingToolbar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val intent = requireActivity().intent
        if (intent != null && intent.getBooleanExtra(AppUpdates.KEY_NEW_UPDATE, false)) {
            (requireActivity().findViewById<View>(R.id.bottomNavigationView) as BottomNavigationView)
                .selectedItemId = R.id.settings
            replaceFragment(parentFragmentManager, AppUpdates())
            intent.removeExtra(AppUpdates.KEY_NEW_UPDATE)
        } else {
            scheduleUpdates(appContext)
        }

        appBarLayout = view.findViewById(R.id.appBarLayout)
        appBarLayout.addOnOffsetChangedListener(this)

        if (isToolbarFullyExpanded) {
            listView.scrollToPosition(0)
        }

        linkMonetColorsIfRequired()
        putBoolean(FIRST_INSTALL, false)
        putBoolean(UPDATE_DETECTED, false)
        saveVersionCode()
        saveBootId
        migrateToRoomDatabase()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        findPreference<UpdateCheckerPreference>("newUpdate")?.apply {
            onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    replaceFragment(parentFragmentManager, AppUpdates())
                    true
                }

            checkForUpdate()
        }
    }

    private fun linkMonetColorsIfRequired() {
        if (getBoolean(FIRST_INSTALL, true) &&
            !getBoolean(UPDATE_DETECTED, false) &&
            !OverlayUtils.isOverlayEnabled("IconifyComponentAMAC.overlay") &&
            !OverlayUtils.isOverlayEnabled("IconifyComponentAMGC.overlay") &&
            !FabricatedUtils.isOverlayEnabled(COLOR_ACCENT_PRIMARY) &&
            !FabricatedUtils.isOverlayEnabled(COLOR_ACCENT_PRIMARY_LIGHT) &&
            !FabricatedUtils.isOverlayEnabled(COLOR_ACCENT_SECONDARY) &&
            !FabricatedUtils.isOverlayEnabled(COLOR_ACCENT_SECONDARY_LIGHT)
        ) {
            enableOverlay("IconifyComponentAMGC.overlay")
        }
    }

    override fun onOffsetChanged(appBarLayout: AppBarLayout?, verticalOffset: Int) {
        if (verticalOffset == 0) {
            if (!isToolbarFullyExpanded) {
                listView.scrollToPosition(0)
                isToolbarFullyExpanded = true
            }
        } else {
            isToolbarFullyExpanded = false
        }
    }

    override fun onResume() {
        super.onResume()

        if (isToolbarFullyExpanded) {
            listView.scrollToPosition(0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        appBarLayout.removeOnOffsetChangedListener(this)
    }

    companion object {
        private var isToolbarFullyExpanded = true
    }
}
