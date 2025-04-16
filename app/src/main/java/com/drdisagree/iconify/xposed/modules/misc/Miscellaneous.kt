package com.drdisagree.iconify.xposed.modules.misc

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.XResources
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.common.Preferences.FIXED_STATUS_ICONS_SIDEMARGIN
import com.drdisagree.iconify.data.common.Preferences.FIXED_STATUS_ICONS_SWITCH
import com.drdisagree.iconify.data.common.Preferences.FIXED_STATUS_ICONS_TOPMARGIN
import com.drdisagree.iconify.data.common.Preferences.HEADER_CLOCK_SWITCH
import com.drdisagree.iconify.data.common.Preferences.HIDE_DATA_DISABLED_ICON
import com.drdisagree.iconify.data.common.Preferences.HIDE_STATUS_ICONS_SWITCH
import com.drdisagree.iconify.data.common.Preferences.QSPANEL_HIDE_CARRIER
import com.drdisagree.iconify.data.common.References.FABRICATED_SB_COLOR_SOURCE
import com.drdisagree.iconify.xposed.HookRes.Companion.resParams
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getFieldSilently
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookLayout
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setFieldSilently
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
class Miscellaneous(context: Context) : ModPack(context) {

    private var hideQsCarrierGroup = false
    private var hideStatusIcons = false
    private var fixedStatusIcons = false
    private var hideDataDisabledIcon = false
    private var sideMarginStatusIcons = 0
    private var topMarginStatusIcons = 8
    private var statusIcons: LinearLayout? = null
    private var statusIconContainer: LinearLayout? = null
    private var mobileSignalControllerParam: Any? = null
    private var showHeaderClockA14 = false
    private var coloredStatusbarOverlayEnabled = false

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            hideQsCarrierGroup = getBoolean(QSPANEL_HIDE_CARRIER, false)
            hideStatusIcons = getBoolean(HIDE_STATUS_ICONS_SWITCH, false)
            fixedStatusIcons = getBoolean(FIXED_STATUS_ICONS_SWITCH, false)
            topMarginStatusIcons = getSliderInt(FIXED_STATUS_ICONS_TOPMARGIN, 8)
            sideMarginStatusIcons = getSliderInt(FIXED_STATUS_ICONS_SIDEMARGIN, 0)
            hideDataDisabledIcon = getBoolean(HIDE_DATA_DISABLED_ICON, false)
            showHeaderClockA14 = getBoolean(HEADER_CLOCK_SWITCH, false) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            coloredStatusbarOverlayEnabled = getBoolean("IconifyComponentSBTint.overlay") ||
                    getString(FABRICATED_SB_COLOR_SOURCE, "System") == "Custom"
        }

        when (key.firstOrNull()) {
            QSPANEL_HIDE_CARRIER -> hideQSCarrierGroup()

            HIDE_STATUS_ICONS_SWITCH -> hideStatusIcons()

            in setOf(
                FIXED_STATUS_ICONS_SWITCH,
                HIDE_STATUS_ICONS_SWITCH,
                FIXED_STATUS_ICONS_TOPMARGIN,
                FIXED_STATUS_ICONS_SIDEMARGIN
            ) -> fixedStatusIconsA12()

            HIDE_DATA_DISABLED_ICON -> mobileSignalControllerParam.callMethod("updateTelephony")
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        hideElements()
        hideQSCarrierGroup()
        hideStatusIcons()
        fixedStatusIconsA12()
        hideDataDisabledIcon()
        fixRotationViewColor()
    }

    private fun hideElements() {
        val quickStatusBarHeader = findClass("$SYSTEMUI_PACKAGE.qs.QuickStatusBarHeader")

        quickStatusBarHeader
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                if (hideStatusIcons) {
                    (param.thisObject.getFieldSilently("mDateView") as? View)?.apply {
                        layoutParams.height = 0
                        layoutParams.width = 0
                        visibility = View.INVISIBLE
                    }

                    (param.thisObject.getFieldSilently("mClockDateView") as? TextView)?.apply {
                        visibility = View.INVISIBLE
                        setTextAppearance(0)
                        setTextColor(0)
                    }

                    (param.thisObject.getFieldSilently("mClockView") as? TextView)?.apply {
                        visibility = View.INVISIBLE
                        setTextAppearance(0)
                        setTextColor(0)
                    }
                }

                if (hideStatusIcons || hideQsCarrierGroup) {
                    val mQSCarriers = param.thisObject.getFieldSilently("mQSCarriers") as? View
                    mQSCarriers?.visibility = View.INVISIBLE
                }
            }

        val shadeHeaderControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.shade.LargeScreenShadeHeaderController",
            "$SYSTEMUI_PACKAGE.shade.ShadeHeaderController"
        )

        shadeHeaderControllerClass
            .hookMethod("onInit")
            .runAfter { param ->
                if (hideStatusIcons) {
                    val iconContainer = param.thisObject.getFieldSilently(
                            "iconContainer"
                    ) as? LinearLayout
                    (iconContainer?.parent as? ViewGroup)?.removeView(iconContainer)

                    val batteryIcon = param.thisObject.getFieldSilently(
                            "batteryIcon"
                    ) as? LinearLayout
                    (batteryIcon?.parent as? ViewGroup)?.removeView(batteryIcon)
                }

                if (hideStatusIcons || hideQsCarrierGroup) {
                    val qsCarrierGroup = param.thisObject.getFieldSilently(
                            "qsCarrierGroup"
                    ) as? LinearLayout
                    (qsCarrierGroup?.parent as? ViewGroup)?.removeView(qsCarrierGroup)

                    val mShadeCarrierGroup = param.thisObject.getFieldSilently(
                            "mShadeCarrierGroup"
                    ) as? LinearLayout
                    (mShadeCarrierGroup?.parent as? ViewGroup)?.removeView(mShadeCarrierGroup)
                }
            }
    }

    private fun hideDataDisabledIcon() {
        val mobileSignalControllerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.connectivity.MobileSignalController")
        val signalIconModelCellularClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.pipeline.mobile.domain.model.SignalIconModel\$Cellular",
            suppressError = true
        )

        var alwaysShowDataRatIcon = false
        var mDataDisabledIcon = false

        mobileSignalControllerClass
            .hookMethod("updateTelephony")
            .runBefore { param ->
                if (mobileSignalControllerParam == null) {
                    mobileSignalControllerParam = param.thisObject
                }

                if (!hideDataDisabledIcon) return@runBefore

                alwaysShowDataRatIcon = param.thisObject
                    .getField("mConfig")
                    .getField("alwaysShowDataRatIcon") as Boolean

                param.thisObject
                    .getField("mConfig")
                    .setField("alwaysShowDataRatIcon", true)

                try {
                    mDataDisabledIcon = param.thisObject.getField(
                        "mDataDisabledIcon"
                    ) as Boolean

                    param.thisObject.setField("mDataDisabledIcon", false)
                } catch (ignored: Throwable) {
                }
            }
            .runAfter { param ->
                if (mobileSignalControllerParam == null) {
                    mobileSignalControllerParam = param.thisObject
                }

                if (!hideDataDisabledIcon) return@runAfter

                param.thisObject
                    .getField("mConfig")
                    .setField("alwaysShowDataRatIcon", alwaysShowDataRatIcon)

                param.thisObject.setFieldSilently("mDataDisabledIcon", mDataDisabledIcon)
            }

        signalIconModelCellularClass
            .hookConstructor()
            .runAfter { param ->
                if (!hideDataDisabledIcon) return@runAfter

                param.thisObject.setField("showExclamationMark", false)
            }
    }

    private fun hideQSCarrierGroup() {
        val xResources: XResources = resParams[SYSTEMUI_PACKAGE]?.res ?: return

        xResources
            .hookLayout()
            .packageName(SYSTEMUI_PACKAGE)
            .resource("layout", "quick_qs_status_icons")
            .suppressError()
            .run { liparam ->
                if (!hideQsCarrierGroup || showHeaderClockA14) return@run

                liparam.view.findViewById<LinearLayout>(
                    liparam.res.getIdentifier(
                        "carrier_group",
                        "id",
                        mContext.packageName
                    )
                ).apply {
                    layoutParams.height = 0
                    layoutParams.width = 0
                    minimumWidth = 0
                    visibility = View.INVISIBLE
                }
            }
    }

    private fun hideStatusIcons() {
        val xResources: XResources = resParams[SYSTEMUI_PACKAGE]?.res ?: return

        xResources
            .hookLayout()
            .packageName(SYSTEMUI_PACKAGE)
            .resource("layout", "quick_qs_status_icons")
            .suppressError()
            .run { liparam ->
                if (!hideStatusIcons) return@run

                try {
                    liparam.view.findViewById<TextView>(
                        liparam.res.getIdentifier(
                            "clock",
                            "id",
                            mContext.packageName
                        )
                    ).apply {
                        layoutParams.height = 0
                        layoutParams.width = 0
                        setTextAppearance(0)
                        setTextColor(0)
                    }
                } catch (ignored: Throwable) {
                }

                try {
                    liparam.view.findViewById<TextView>(
                        liparam.res.getIdentifier(
                            "date_clock",
                            "id",
                            mContext.packageName
                        )
                    ).apply {
                        layoutParams.height = 0
                        layoutParams.width = 0
                        setTextAppearance(0)
                        setTextColor(0)
                    }
                } catch (ignored: Throwable) {
                }

                if (!showHeaderClockA14) {
                    try {
                        liparam.view.findViewById<LinearLayout>(
                            liparam.res.getIdentifier(
                                "carrier_group",
                                "id",
                                mContext.packageName
                            )
                        ).apply {
                            layoutParams.height = 0
                            layoutParams.width = 0
                            minimumWidth = 0
                            visibility = View.INVISIBLE
                        }
                    } catch (ignored: Throwable) {
                    }

                    try {
                        liparam.view.findViewById<LinearLayout>(
                            liparam.res.getIdentifier(
                                "statusIcons",
                                "id",
                                mContext.packageName
                            )
                        ).apply {
                            layoutParams.height = 0
                            layoutParams.width = 0
                        }
                    } catch (ignored: Throwable) {
                    }

                    try {
                        liparam.view.findViewById<LinearLayout>(
                            liparam.res.getIdentifier(
                                "batteryRemainingIcon",
                                "id",
                                mContext.packageName
                            )
                        ).apply {
                            layoutParams.height = 0
                            layoutParams.width = 0
                        }
                    } catch (ignored: Throwable) {
                    }
                }

                try {
                    liparam.view.findViewById<FrameLayout>(
                        liparam.res.getIdentifier(
                            "rightLayout",
                            "id",
                            mContext.packageName
                        )
                    ).apply {
                        layoutParams.height = 0
                        layoutParams.width = 0
                        visibility = View.INVISIBLE
                    }
                } catch (ignored: Throwable) {
                }

                // Ricedroid date
                try {
                    liparam.view.findViewById<TextView>(
                        liparam.res.getIdentifier(
                            "date",
                            "id",
                            mContext.packageName
                        )
                    ).apply {
                        layoutParams.height = 0
                        layoutParams.width = 0
                        setTextAppearance(0)
                        setTextColor(0)
                    }
                } catch (ignored: Throwable) {
                }

                // Nusantara clock
                try {
                    liparam.view.findViewById<TextView>(
                        liparam.res.getIdentifier(
                            "jr_clock",
                            "id",
                            mContext.packageName
                        )
                    ).apply {
                        layoutParams.height = 0
                        layoutParams.width = 0
                        setTextAppearance(0)
                        setTextColor(0)
                    }
                } catch (ignored: Throwable) {
                }

                // Nusantara date
                try {
                    val jrDateContainer =
                        liparam.view.findViewById<LinearLayout>(
                            liparam.res.getIdentifier(
                                "jr_date_container",
                                "id",
                                mContext.packageName
                            )
                        )
                    (jrDateContainer.getChildAt(0) as TextView).apply {
                        layoutParams.height = 0
                        layoutParams.width = 0
                        setTextAppearance(0)
                        setTextColor(0)
                    }
                } catch (ignored: Throwable) {
                }
            }

        xResources
            .hookLayout()
            .packageName(SYSTEMUI_PACKAGE)
            .resource("layout", "quick_status_bar_header_date_privacy")
            .suppressError()
            .run { liparam ->
                if (!hideStatusIcons) return@run

                try {
                    liparam.view.findViewById<TextView>(
                        liparam.res.getIdentifier(
                            "date",
                            "id",
                            mContext.packageName
                        )
                    ).apply {
                        setTextAppearance(0)
                        layoutParams.height = 0
                        layoutParams.width = 0
                        setTextAppearance(0)
                        setTextColor(0)
                    }
                } catch (ignored: Throwable) {
                }
            }
    }

    private fun fixedStatusIconsA12() {
        if (Build.VERSION.SDK_INT >= 33) return

        val xResources: XResources = resParams[SYSTEMUI_PACKAGE]?.res ?: return

        xResources
            .hookLayout()
            .packageName(SYSTEMUI_PACKAGE)
            .resource("layout", "quick_qs_status_icons")
            .suppressError()
            .run { liparam ->
                if (!fixedStatusIcons || hideStatusIcons) return@run

                try {
                    statusIcons = liparam.view.findViewById(
                        liparam.res.getIdentifier(
                            "statusIcons",
                            "id",
                            mContext.packageName
                        )
                    )

                    if (statusIcons != null) {
                        statusIconContainer = statusIcons!!.parent as LinearLayout
                        statusIcons!!.layoutParams.height = 0
                        statusIcons!!.layoutParams.width = 0
                        statusIcons!!.visibility = View.GONE
                        statusIcons!!.requestLayout()
                    }

                    val batteryRemainingIcon = liparam.view.findViewById<LinearLayout>(
                        liparam.res.getIdentifier(
                            "batteryRemainingIcon",
                            "id",
                            mContext.packageName
                        )
                    )

                    batteryRemainingIcon?.let {
                        (it.layoutParams as LinearLayout.LayoutParams)
                            .weight = 0f
                        it.layoutParams.height = 0
                        it.layoutParams.width = 0
                        it.visibility = View.GONE
                        it.requestLayout()
                    }
                } catch (ignored: Throwable) {
                }
            }

        xResources
            .hookLayout()
            .packageName(SYSTEMUI_PACKAGE)
            .resource("layout", "quick_status_bar_header_date_privacy")
            .suppressError()
            .run { liparam ->
                if (!fixedStatusIcons || hideStatusIcons) return@run

                try {
                    val privacyContainer =
                        liparam.view.findViewById<FrameLayout>(
                            liparam.res.getIdentifier(
                                "privacy_container",
                                "id",
                                mContext.packageName
                            )
                        )

                    if (statusIconContainer != null && statusIconContainer!!.parent != null && statusIcons != null) {
                        try {
                            (statusIconContainer!!.parent as FrameLayout).removeView(
                                statusIconContainer
                            )
                        } catch (ignored: Throwable) {
                            (statusIconContainer!!.parent as LinearLayout).removeView(
                                statusIconContainer
                            )
                        }

                        val statusIcons =
                            statusIconContainer!!.getChildAt(0) as LinearLayout
                        statusIcons.let {
                            it.layoutParams.height = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                28f,
                                mContext.resources.displayMetrics
                            ).toInt()
                            it.layoutParams.width =
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            it.visibility = View.VISIBLE
                            it.requestLayout()
                        }

                        val batteryRemainingIcon =
                            (statusIconContainer!!.getChildAt(1) as LinearLayout)
                        batteryRemainingIcon.let {
                            (it.layoutParams as LinearLayout.LayoutParams).weight =
                                1f
                            it.layoutParams.height =
                                TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP,
                                    28f,
                                    mContext.resources.displayMetrics
                                ).toInt()
                            it.layoutParams.width = 0
                            it.visibility = View.VISIBLE
                            it.requestLayout()
                        }

                        statusIconContainer!!.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                28f,
                                mContext.resources.displayMetrics
                            ).toInt(),
                            Gravity.END
                        )
                        statusIconContainer!!.gravity = Gravity.CENTER
                        (statusIconContainer!!.layoutParams as FrameLayout.LayoutParams).setMargins(
                            0, TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                topMarginStatusIcons.toFloat(),
                                mContext.resources.displayMetrics
                            ).toInt(), 0, 0
                        )
                        (statusIconContainer!!.layoutParams as FrameLayout.LayoutParams).marginEnd =
                            TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                sideMarginStatusIcons.toFloat(),
                                mContext.resources.displayMetrics
                            ).toInt()
                        statusIconContainer!!.requestLayout()

                        privacyContainer.addView(statusIconContainer)
                    }
                } catch (ignored: Throwable) {
                }
            }
    }

    private fun fixRotationViewColor() {
        val floatingRotationButtonClass =
            findClass("$SYSTEMUI_PACKAGE.shared.rotation.FloatingRotationButton")

        floatingRotationButtonClass
            .hookMethod("updateIcon")
            .runBefore { param ->
                if (coloredStatusbarOverlayEnabled) {
                    param.args[1] = Color.BLACK
                }
            }
    }
}