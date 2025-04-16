package com.drdisagree.iconify.xposed.modules.settings

import android.annotation.SuppressLint
import android.content.Context
import com.drdisagree.iconify.data.common.Const.SETTINGS_PACKAGE
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
class ZenPriorityModeIcon(context: Context) : ModPack(context) {

    private var replaceZenModeIcon = false

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            replaceZenModeIcon = getBoolean("IconifyComponentSIP1.overlay", false)
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val topLevelSettingsClass = findClass("$SETTINGS_PACKAGE.homepage.TopLevelSettings")

        topLevelSettingsClass
            .hookMethod("onCreateAdapter")
            .runBefore { param ->
                if (!replaceZenModeIcon) return@runBefore

                val preferenceScreen = param.args[0]

                preferenceScreen.callMethod(
                    "findPreference",
                    "top_level_priority_modes"
                )?.callMethod(
                    "setIcon",
                    mContext.resources.getIdentifier(
                        "ic_suggestion_dnd",
                        "drawable",
                        mContext.packageName
                    )
                )
            }
    }
}