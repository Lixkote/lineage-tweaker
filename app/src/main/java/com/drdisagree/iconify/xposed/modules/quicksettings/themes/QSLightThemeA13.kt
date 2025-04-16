package com.drdisagree.iconify.xposed.modules.quicksettings.themes


import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.service.quicksettings.Tile
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.common.Preferences.CUSTOM_QS_TEXT_COLOR
import com.drdisagree.iconify.data.common.Preferences.DUALTONE_QSPANEL
import com.drdisagree.iconify.data.common.Preferences.LIGHT_QSPANEL
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.SettingsLibUtils.Companion.getColorAttr
import com.drdisagree.iconify.xposed.modules.extras.utils.SettingsLibUtils.Companion.getColorAttrDefaultColor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethodMatchPattern
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setField
import com.drdisagree.iconify.xposed.utils.SystemUtils
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Arrays

@SuppressLint("DiscouragedApi")
class QSLightThemeA13(context: Context) : ModPack(context) {

    private var mBehindColors: Any? = null
    private var isDark: Boolean
    private var colorActive: Int? = null
    private var colorInactive: Int? = null
    private var mClockViewQSHeader: Any? = null
    private var unlockedScrimState: Any? = null
    private var customQsTextColor = false
    private val qsLightThemeOverlay = "IconifyComponentQSLT.overlay"
    private val qsDualToneOverlay = "IconifyComponentQSDT.overlay"

    init {
        isDark = SystemUtils.isDarkMode
    }

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            lightQSHeaderEnabled = getBoolean(LIGHT_QSPANEL, false)
            dualToneQSEnabled = lightQSHeaderEnabled && getBoolean(DUALTONE_QSPANEL, false)
            customQsTextColor = getBoolean(CUSTOM_QS_TEXT_COLOR, false)
        }

        if (key.isNotEmpty()) {
            key[0].let {
                if (it == LIGHT_QSPANEL || it == DUALTONE_QSPANEL) {
                    applyOverlays(true)
                }
            }
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val qsTileViewImplClass = findClass("$SYSTEMUI_PACKAGE.qs.tileimpl.QSTileViewImpl")
        val fragmentHostManagerClass = findClass("$SYSTEMUI_PACKAGE.fragments.FragmentHostManager")
        val scrimControllerClass = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimController")
        val gradientColorsClass =
            findClass("com.android.internal.colorextraction.ColorExtractor\$GradientColors")!!
        val qsPanelControllerClass = findClass("$SYSTEMUI_PACKAGE.qs.QSPanelController")
        val interestingConfigChangesClass =
            findClass("com.android.settingslib.applications.InterestingConfigChanges")!!
        val scrimStateEnum = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimState")!!
        val qsIconViewImplClass = findClass("$SYSTEMUI_PACKAGE.qs.tileimpl.QSIconViewImpl")
        val centralSurfacesImplClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.phone.CentralSurfacesImpl",
            suppressError = true
        )
        val clockClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.policy.Clock",
            suppressError = true
        )
        val quickStatusBarHeaderClass = findClass("$SYSTEMUI_PACKAGE.qs.QuickStatusBarHeader")

        val batteryStatusChipClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.BatteryStatusChip",
            suppressError = true
        )

        // Background color of android 14's charging chip. Fix for light QS theme situation
        batteryStatusChipClass
            .hookMethod("updateResources")
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                (param.thisObject.getField("roundedContainer") as LinearLayout)
                    .background.setTint(colorActive!!)

                val colorPrimary: Int = getColorAttrDefaultColor(
                    mContext,
                    android.R.attr.textColorPrimaryInverse
                )
                val textColorSecondary: Int = getColorAttrDefaultColor(
                    mContext,
                    android.R.attr.textColorSecondaryInverse
                )

                param.thisObject.getField("batteryMeterView").callMethod(
                    "updateColors",
                    colorPrimary,
                    textColorSecondary,
                    colorPrimary
                )
            }

        unlockedScrimState = scrimStateEnum.enumConstants?.let {
            Arrays.stream(it)
                .filter { c: Any -> c.toString() == "UNLOCKED" }
                .findFirst().get()
        }

        unlockedScrimState?.javaClass
            ?.hookMethod("prepare")
            ?.runAfter {
                if (!lightQSHeaderEnabled) return@runAfter

                unlockedScrimState.setField("mBehindTint", Color.TRANSPARENT)
            }

        qsPanelControllerClass
            .hookConstructor()
            .runAfter { calculateColors() }

        try { // 13QPR1
            val qsFgsManagerFooterClass = findClass(
                "$SYSTEMUI_PACKAGE.qs.QSFgsManagerFooter",
                throwException = true
            )
            val footerActionsControllerClass =
                findClass("$SYSTEMUI_PACKAGE.qs.FooterActionsController")

            qsFgsManagerFooterClass
                .hookConstructor()
                .runAfter { param ->
                    if (!lightQSHeaderEnabled || isDark) return@runAfter

                    try {
                        (param.thisObject.getField("mNumberContainer") as View)
                            .background.setTint(colorInactive!!)
                    } catch (throwable: Throwable) {
                        log(this@QSLightThemeA13, throwable)
                    }
                }

            footerActionsControllerClass
                .hookConstructor()
                .runAfter { param ->
                    if (!lightQSHeaderEnabled || isDark) return@runAfter

                    try {
                        val res = mContext.resources
                        val view = param.args[0] as ViewGroup

                        view.findViewById<View>(
                            res.getIdentifier(
                                "multi_user_switch",
                                "id",
                                mContext.packageName
                            )
                        ).background.setTint(
                            colorInactive!!
                        )

                        val settingsButtonContainer = view.findViewById<View>(
                            res.getIdentifier(
                                "settings_button_container",
                                "id",
                                mContext.packageName
                            )
                        )

                        settingsButtonContainer.background.setTint(colorInactive!!)
                    } catch (throwable: Throwable) {
                        log(this@QSLightThemeA13, throwable)
                    }
                }

            // White QS Clock bug - doesn't seem applicable on 13QPR3 and 14
            quickStatusBarHeaderClass
                .hookMethod("onFinishInflate")
                .runAfter { param ->
                    val thisView = param.thisObject as View
                    val res = mContext.resources

                    mClockViewQSHeader = try {
                        param.thisObject.getField("mClockView")
                    } catch (ignored: Throwable) {
                        thisView.findViewById(
                            res.getIdentifier(
                                "clock",
                                "id",
                                mContext.packageName
                            )
                        )
                    } as? View

                    if (lightQSHeaderEnabled && !isDark && mClockViewQSHeader != null) {
                        try {
                            (mClockViewQSHeader as TextView).setTextColor(Color.BLACK)
                        } catch (throwable: Throwable) {
                            log(this@QSLightThemeA13, throwable)
                        }
                    }
                }

            // White QS Clock bug - doesn't seem applicable on 13QPR3 and 14
            clockClass
                .hookMethod("onColorsChanged")
                .runAfter {
                    val isLight = isDark // reverse logic

                    if (lightQSHeaderEnabled && isLight && mClockViewQSHeader != null) {
                        try {
                            (mClockViewQSHeader as TextView).setTextColor(Color.BLACK)
                        } catch (throwable: Throwable) {
                            log(this@QSLightThemeA13, throwable)
                        }
                    }
                }
        } catch (throwable: Throwable) { // 13QPR2&3
            // 13QPR3
            val shadeHeaderControllerClass = findClass(
                "$SYSTEMUI_PACKAGE.shade.ShadeHeaderController",
                "$SYSTEMUI_PACKAGE.shade.LargeScreenShadeHeaderController"
            )
            val qsContainerImplClass = findClass("$SYSTEMUI_PACKAGE.qs.QSContainerImpl")

            shadeHeaderControllerClass
                .hookMethod("onInit")
                .runAfter { param ->
                    try {
                        val mView =
                            param.thisObject.getField("mView") as View
                        val iconManager =
                            param.thisObject.getField("iconManager")
                        val batteryIcon =
                            param.thisObject.getField("batteryIcon")
                        val configurationControllerListener = param.thisObject.getField(
                            "configurationControllerListener"
                        )

                        configurationControllerListener.javaClass
                            .hookMethod(
                                "onConfigChanged",
                                "onDensityOrFontScaleChanged",
                                "onUiModeChanged",
                                "onThemeChanged"
                            )
                            .runAfter { setHeaderComponentsColor(mView, iconManager, batteryIcon) }

                        setHeaderComponentsColor(mView, iconManager, batteryIcon)
                    } catch (throwable: Throwable) {
                        log(this@QSLightThemeA13, throwable)
                    }
                }

            qsContainerImplClass
                .hookMethod("updateResources")
                .runAfter { param ->
                    if (!lightQSHeaderEnabled || isDark) return@runAfter

                    try {
                        val res = mContext.resources
                        val view = param.thisObject as ViewGroup

                        val settingsButtonContainer = view.findViewById<View>(
                            res.getIdentifier(
                                "settings_button_container",
                                "id",
                                mContext.packageName
                            )
                        )
                        settingsButtonContainer.background.setTint(colorInactive!!)

                        val settingsIcon =
                            settingsButtonContainer.findViewById<ImageView>(
                                res.getIdentifier(
                                    "icon",
                                    "id",
                                    mContext.packageName
                                )
                            )
                        settingsIcon.imageTintList = ColorStateList.valueOf(Color.BLACK)

                        val pmButtonContainer = view.findViewById<View>(
                            res.getIdentifier(
                                "pm_lite",
                                "id",
                                mContext.packageName
                            )
                        )

                        val pmIcon = pmButtonContainer.findViewById<ImageView>(
                            res.getIdentifier(
                                "icon",
                                "id",
                                mContext.packageName
                            )
                        )
                        pmIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
                    } catch (throwable: Throwable) {
                        log(this@QSLightThemeA13, throwable)
                    }
                }
        }

        qsIconViewImplClass
            .hookMethod("setIcon")
            .runBefore { param ->
                if (!lightQSHeaderEnabled || isDark) return@runBefore

                try {
                    if (param.args[0] is ImageView &&
                        param.args[1].getField("state") as Int == Tile.STATE_ACTIVE
                    ) {
                        param.thisObject.setField("mTint", colorInactive)
                    }
                } catch (throwable: Throwable) {
                    log(this@QSLightThemeA13, throwable)
                }
            }

        centralSurfacesImplClass
            .hookConstructor()
            .runAfter { applyOverlays(true) }

        centralSurfacesImplClass
            .hookMethod("updateTheme")
            .runAfter { applyOverlays(false) }

        qsTileViewImplClass
            .hookConstructor()
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                try {
                    if (!customQsTextColor) {
                        param.thisObject.setField("colorLabelActive", Color.WHITE)
                        param.thisObject.setField("colorSecondaryLabelActive", -0x7f000001)
                    }

                    param.thisObject.setField("colorLabelInactive", Color.BLACK)
                    param.thisObject.setField("colorSecondaryLabelInactive", -0x80000000)
                } catch (throwable: Throwable) {
                    log(this@QSLightThemeA13, throwable)
                }
            }

        qsIconViewImplClass
            .hookMethod("getIconColorForState", "getColor")
            .runBefore { param ->
                if (!lightQSHeaderEnabled || isDark) return@runBefore

                val (isDisabledState: Boolean, isActiveState: Boolean) = Utils.getTileState(param)

                if (isDisabledState) {
                    param.result = -0x80000000
                } else {
                    if (isActiveState && !customQsTextColor) {
                        param.result = Color.WHITE
                    } else if (!isActiveState) {
                        param.result = Color.BLACK
                    }
                }
            }

        qsIconViewImplClass
            .hookMethod("updateIcon")
            .runAfter { param ->
                if (!lightQSHeaderEnabled || isDark) return@runAfter

                val (isDisabledState: Boolean, isActiveState: Boolean) = Utils.getTileState(param)

                val mIcon = param.args[0] as ImageView

                if (isDisabledState) {
                    param.result = -0x80000000
                } else {
                    if (isActiveState && !customQsTextColor) {
                        mIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
                    } else if (!isActiveState) {
                        mIcon.imageTintList = ColorStateList.valueOf(Color.BLACK)
                    }
                }
            }

        try {
            mBehindColors = gradientColorsClass.getDeclaredConstructor().newInstance()
        } catch (throwable: Throwable) {
            log(this@QSLightThemeA13, throwable)
        }

        scrimControllerClass
            .hookMethod("updateScrims")
            .runAfter { param ->
                if (!dualToneQSEnabled) return@runAfter

                try {
                    val mScrimBehind = param.thisObject.getField("mScrimBehind")
                    val mBlankScreen = param.thisObject.getField("mBlankScreen") as Boolean
                    val alpha = mScrimBehind.getField("mViewAlpha") as Float
                    val animateBehindScrim = alpha != 0f && !mBlankScreen

                    mScrimBehind.callMethod(
                        "setColors",
                        mBehindColors,
                        animateBehindScrim
                    )
                } catch (throwable: Throwable) {
                    log(this@QSLightThemeA13, throwable)
                }
            }

        scrimControllerClass
            .hookMethod("updateThemeColors")
            .runAfter {
                calculateColors()

                if (!dualToneQSEnabled) return@runAfter

                try {
                    val states: ColorStateList = getColorAttr(
                        mContext.resources.getIdentifier(
                            "android:attr/colorSurfaceHeader",
                            "attr",
                            mContext.packageName
                        ), mContext
                    )
                    val surfaceBackground = states.defaultColor
                    val accentStates: ColorStateList =
                        getColorAttr(
                            mContext.resources.getIdentifier(
                                "colorAccent",
                                "attr",
                                "android"
                            ), mContext
                        )
                    val accent = accentStates.defaultColor

                    mBehindColors.callMethod("setMainColor", surfaceBackground)
                    mBehindColors.callMethod("setSecondaryColor", accent)

                    val contrast = ColorUtils.calculateContrast(
                        mBehindColors.callMethod(
                            "getMainColor"
                        ) as Int, Color.WHITE
                    )

                    mBehindColors.callMethod("setSupportsDarkText", contrast > 4.5)
                } catch (throwable: Throwable) {
                    log(this@QSLightThemeA13, throwable)
                }
            }

        scrimControllerClass
            .hookMethodMatchPattern("applyState.*")
            .runAfter { param ->
                if (!lightQSHeaderEnabled) return@runAfter

                try {
                    val mClipsQsScrim =
                        param.thisObject.getField("mClipsQsScrim") as Boolean
                    if (mClipsQsScrim) {
                        param.thisObject.setField("mBehindTint", Color.TRANSPARENT)
                    }
                } catch (throwable: Throwable) {
                    log(this@QSLightThemeA13, throwable)
                }
            }

        val constants: Array<out Any> = scrimStateEnum.enumConstants ?: arrayOf()
        constants.forEach { constant ->
            when (constant.toString()) {
                "KEYGUARD" -> constant.javaClass
                    .hookMethod("prepare")
                    .runAfter { param ->
                        if (!lightQSHeaderEnabled) return@runAfter

                        val mClipQsScrim = param.thisObject.getField(
                            "mClipQsScrim"
                        ) as Boolean

                        if (mClipQsScrim) {
                            val mScrimBehind = param.thisObject.getField(
                                "mScrimBehind"
                            )
                            val mTintColor = mScrimBehind.getField("mTintColor") as Int

                            if (mTintColor != Color.TRANSPARENT) {
                                mScrimBehind.setField(
                                    "mTintColor",
                                    Color.TRANSPARENT
                                )

                                mScrimBehind.callMethod(
                                    "updateColorWithTint",
                                    false
                                )
                            }

                            mScrimBehind.callMethod("setViewAlpha", 1f)
                        }
                    }

                "BOUNCER" -> constant.javaClass
                    .hookMethod("prepare")
                    .runAfter { param ->
                        if (!lightQSHeaderEnabled) return@runAfter

                        param.thisObject.setField(
                            "mBehindTint",
                            Color.TRANSPARENT
                        )
                    }

                "SHADE_LOCKED" -> {
                    constant.javaClass
                        .hookMethod("prepare")
                        .runAfter { param ->
                            if (!lightQSHeaderEnabled) return@runAfter

                            param.thisObject.setField(
                                "mBehindTint",
                                Color.TRANSPARENT
                            )

                            val mClipQsScrim = param.thisObject.getField(
                                "mClipQsScrim"
                            ) as Boolean

                            if (mClipQsScrim) {
                                val mScrimBehind = param.thisObject.getField(
                                    "mScrimBehind"
                                )
                                val mTintColor = mScrimBehind.getField("mTintColor") as Int

                                if (mTintColor != Color.TRANSPARENT) {
                                    mScrimBehind.setField(
                                        "mTintColor",
                                        Color.TRANSPARENT
                                    )

                                    mScrimBehind.callMethod(
                                        "updateColorWithTint",
                                        false
                                    )
                                }

                                mScrimBehind.callMethod("setViewAlpha", 1f)
                            }
                        }

                    constant.javaClass
                        .hookMethod("getBehindTint")
                        .suppressError()
                        .runBefore { param ->
                            if (!lightQSHeaderEnabled) return@runBefore

                            param.result = Color.TRANSPARENT
                        }
                }

                "UNLOCKED" -> constant.javaClass
                    .hookMethod("prepare")
                    .runAfter { param ->
                        if (!lightQSHeaderEnabled) return@runAfter

                        param.thisObject.setField(
                            "mBehindTint",
                            Color.TRANSPARENT
                        )

                        val mScrimBehind = param.thisObject.getField("mScrimBehind")
                        val mTintColor = mScrimBehind.getField("mTintColor") as Int

                        if (mTintColor != Color.TRANSPARENT) {
                            mScrimBehind.setField(
                                "mTintColor",
                                Color.TRANSPARENT
                            )

                            mScrimBehind.callMethod(
                                "updateColorWithTint",
                                false
                            )
                        }

                        mScrimBehind.callMethod("setViewAlpha", 1f)
                    }
            }
        }

        fragmentHostManagerClass
            .hookConstructor()
            .runBefore { param ->
                try {
                    param.thisObject.setField(
                        "mConfigChanges",
                        interestingConfigChangesClass.getDeclaredConstructor(
                            Int::class.javaPrimitiveType
                        ).newInstance(0x40000000 or 0x0004 or 0x0100 or -0x80000000 or 0x0200)
                    )
                } catch (throwable: Throwable) {
                    log(this@QSLightThemeA13, throwable)
                }
            }
    }

    private fun applyOverlays(force: Boolean) {
        val isCurrentlyDark: Boolean = SystemUtils.isDarkMode
        if (isCurrentlyDark == isDark && !force) return

        isDark = isCurrentlyDark

        calculateColors()

        Utils.disableOverlays(qsLightThemeOverlay, qsDualToneOverlay)

        try {
            Thread.sleep(50)
        } catch (ignored: Throwable) {
        }

        if (lightQSHeaderEnabled) {
            if (!isCurrentlyDark) {
                Utils.enableOverlay(qsLightThemeOverlay)
            }
            if (dualToneQSEnabled) Utils.enableOverlay(qsDualToneOverlay)
        }
    }

    private fun calculateColors() {
        if (!lightQSHeaderEnabled) return

        try {
            if (unlockedScrimState != null) {
                unlockedScrimState.setField("mBehindTint", Color.TRANSPARENT)
            }

            if (!isDark) {
                colorActive = mContext.resources.getColor(
                    mContext.resources.getIdentifier(
                        "android:color/system_accent1_600",
                        "color",
                        mContext.packageName
                    ), mContext.theme
                )
                colorInactive = mContext.resources.getColor(
                    mContext.resources.getIdentifier(
                        "android:color/system_neutral1_10",
                        "color",
                        mContext.packageName
                    ), mContext.theme
                )
            }
        } catch (throwable: Throwable) {
            log(this@QSLightThemeA13, throwable)
        }
    }

    private fun setHeaderComponentsColor(mView: View, iconManager: Any, batteryIcon: Any) {
        if (!lightQSHeaderEnabled) return

        val textColorPrimary: Int =
            getColorAttrDefaultColor(android.R.attr.textColorPrimary, mContext)
        val textColorSecondary: Int =
            getColorAttrDefaultColor(android.R.attr.textColorSecondary, mContext)

        try {
            (mView.findViewById<View>(
                mContext.resources.getIdentifier(
                    "clock",
                    "id",
                    mContext.packageName
                )
            ) as TextView).setTextColor(textColorPrimary)

            (mView.findViewById<View>(
                mContext.resources.getIdentifier(
                    "date",
                    "id",
                    mContext.packageName
                )
            ) as TextView).setTextColor(textColorPrimary)
        } catch (ignored: Throwable) {
        }

        try {
            iconManager.callMethod("setTint", textColorPrimary)

            for (i in 1..3) {
                val id = String.format("carrier%s", i)
                try {
                    (mView.findViewById<View>(
                        mContext.resources.getIdentifier(
                            id,
                            "id",
                            mContext.packageName
                        )
                    ).getField("mCarrierText") as TextView).setTextColor(textColorPrimary)

                    (mView.findViewById<View>(
                        mContext.resources.getIdentifier(
                            id,
                            "id",
                            mContext.packageName
                        )
                    ).getField("mMobileSignal") as ImageView).imageTintList =
                        ColorStateList.valueOf(textColorPrimary)

                    (mView.findViewById<View>(
                        mContext.resources.getIdentifier(
                            id,
                            "id",
                            mContext.packageName
                        )
                    ).getField("mMobileRoaming") as ImageView).imageTintList =
                        ColorStateList.valueOf(textColorPrimary)
                } catch (ignored: Throwable) {
                }
            }

            batteryIcon.callMethod(
                "updateColors",
                textColorPrimary,
                textColorSecondary,
                textColorPrimary
            )
        } catch (throwable: Throwable) {
            log(this@QSLightThemeA13, throwable)
        }
    }

    companion object {
        private var lightQSHeaderEnabled = false
        private var dualToneQSEnabled = false
    }
}