package com.drdisagree.iconify.xposed.modules.statusbar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.XResources
import android.view.View
import android.view.ViewGroup
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.common.Preferences.STATUSBAR_SWAP_CELLULAR_NETWORK_TYPE
import com.drdisagree.iconify.xposed.HookRes.Companion.resParams
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.reAddView
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookLayout
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
class SwapSignalNetworkType(context: Context) : ModPack(context) {

    private var swapCellularAndNetworkTypeIcon = false

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            swapCellularAndNetworkTypeIcon = getBoolean(STATUSBAR_SWAP_CELLULAR_NETWORK_TYPE, false)
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val xResources: XResources = resParams[SYSTEMUI_PACKAGE]?.res ?: return

        try {
            xResources
                .hookLayout()
                .packageName(SYSTEMUI_PACKAGE)
                .resource("layout", "status_bar_mobile_signal_group_inner")
                .throwError()
                .run { liparam ->
                    if (!swapCellularAndNetworkTypeIcon) return@run

                    val networkType = liparam.view.findViewById<View>(
                        liparam.res.getIdentifier(
                            "mobile_type_container",
                            "id",
                            mContext.packageName
                        )
                    )
                    val parent = networkType.parent as ViewGroup

                    parent.reAddView(networkType)
                }
        } catch (ignored: Throwable) {
            xResources
                .hookLayout()
                .packageName(SYSTEMUI_PACKAGE)
                .resource("layout", "status_bar_mobile_signal_group")
                .run { liparam ->
                    if (!swapCellularAndNetworkTypeIcon) return@run

                    val networkType = liparam.view.findViewById<View>(
                        liparam.res.getIdentifier(
                            "mobile_type",
                            "id",
                            mContext.packageName
                        )
                    )
                    val parent = networkType.parent as ViewGroup

                    parent.reAddView(networkType)
                }
        }
    }
}