package com.drdisagree.iconify.xposed.modules.quicksettings.themes

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PorterDuff
import android.service.quicksettings.Tile
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.common.Preferences.BLACK_QSPANEL
import com.drdisagree.iconify.data.common.Preferences.CUSTOM_QS_TEXT_COLOR
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.SettingsLibUtils.Companion.getColorAttr
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.findMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethodMatchPattern
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setField
import com.drdisagree.iconify.xposed.utils.SystemUtils
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam


@SuppressLint("DiscouragedApi")
class QSBlackThemeA15(context: Context) : ModPack(context) {

    private var mBehindColors: Any? = null
    private var isDark: Boolean
    private var colorText: Int? = null
    private var colorTextAlpha: Int? = null
    private var mClockViewQSHeader: Any? = null
    private var customQsTextColor = false
    private var shadeCarrierGroupController: Any? = null
    private val modernShadeCarrierGroupMobileViews = ArrayList<Any>()
    private var colorActive: Int? = null
    private var colorInactive: Int? = null
    private var mPrimaryLabelActiveColor: ColorStateList? = null
    private var mSecondaryLabelActiveColor: ColorStateList? = null
    private var mPrimaryLabelInactiveColor: ColorStateList? = null
    private var mSecondaryLabelInactiveColor: ColorStateList? = null

    init {
        isDark = SystemUtils.isDarkMode
    }

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            blackQSHeaderEnabled = getBoolean(BLACK_QSPANEL, false)
            customQsTextColor = getBoolean(CUSTOM_QS_TEXT_COLOR, false)
        }

        initColors(true)
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val qsTileViewImplClass = findClass("$SYSTEMUI_PACKAGE.qs.tileimpl.QSTileViewImpl")
        val fragmentHostManagerClass = findClass("$SYSTEMUI_PACKAGE.fragments.FragmentHostManager")
        val scrimControllerClass = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimController")
        val gradientColorsClass =
            findClass("com.android.internal.colorextraction.ColorExtractor\$GradientColors")!!
        val qsPanelControllerClass = findClass("$SYSTEMUI_PACKAGE.qs.QSPanelController")
        val scrimStateEnum = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimState")!!
        val qsIconViewImplClass = findClass("$SYSTEMUI_PACKAGE.qs.tileimpl.QSIconViewImpl")
        val centralSurfacesImplClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.phone.CentralSurfacesImpl",
            suppressError = true
        )
        val qsCustomizerClass = findClass("$SYSTEMUI_PACKAGE.qs.customize.QSCustomizer")
        val qsContainerImplClass = findClass("$SYSTEMUI_PACKAGE.qs.QSContainerImpl")
        val shadeCarrierClass = findClass("$SYSTEMUI_PACKAGE.shade.carrier.ShadeCarrier")
        val interestingConfigChangesClass =
            findClass("com.android.settingslib.applications.InterestingConfigChanges")!!
        val batteryStatusChipClass = findClass("$SYSTEMUI_PACKAGE.statusbar.BatteryStatusChip")
        val qsFooterViewClass = findClass("$SYSTEMUI_PACKAGE.qs.QSFooterView")
        val brightnessControllerClass =
            findClass("$SYSTEMUI_PACKAGE.settings.brightness.BrightnessController")
        val brightnessMirrorControllerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.policy.BrightnessMirrorController")
        val brightnessSliderControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.settings.brightness.BrightnessSliderController",
            suppressError = true
        )
        val quickStatusBarHeaderClass = findClass("$SYSTEMUI_PACKAGE.qs.QuickStatusBarHeader")
        val clockClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.policy.Clock",
            suppressError = true
        )
        val themeColorKtClass = findClass(
            "com.android.compose.theme.ColorKt",
            suppressError = true
        )
        val expandableControllerImplClass =
            findClass("com.android.compose.animation.ExpandableControllerImpl")
        val footerActionsViewModelClass =
            findClass("$SYSTEMUI_PACKAGE.qs.footer.ui.viewmodel.FooterActionsViewModel")
        val shadeHeaderControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.shade.ShadeHeaderController",
            "$SYSTEMUI_PACKAGE.shade.LargeScreenShadeHeaderController"
        )

        try { // A15 early implementation of QS Footer actions - doesn't seem to be leading to final A15 release
            val footerActionsViewBinderClass = findClass(
                "$SYSTEMUI_PACKAGE.qs.footer.ui.binder.FooterActionsViewBinder",
                throwException = true
            )
            val textButtonViewHolderClass = findClass(
                "$SYSTEMUI_PACKAGE.qs.footer.ui.binder.TextButtonViewHolder",
                throwException = true
            )
            val numberButtonViewHolderClass = findClass(
                "$SYSTEMUI_PACKAGE.qs.footer.ui.binder.NumberButtonViewHolder",
                throwException = true
            )

            // QS security footer count circle
            numberButtonViewHolderClass
                .hookConstructor()
                .runAfter { param ->
                    if (!blackQSHeaderEnabled) return@runAfter

                    (param.thisObject.getField("newDot") as ImageView)
                        .setColorFilter(Color.WHITE)

                    (param.thisObject.getField("number") as TextView)
                        .setTextColor(Color.WHITE)
                }

            // QS security footer
            textButtonViewHolderClass
                .hookConstructor()
                .runAfter { param ->
                    if (!blackQSHeaderEnabled) return@runAfter

                    (param.thisObject.getField("chevron") as ImageView)
                        .setColorFilter(Color.WHITE)

                    (param.thisObject.getField("icon") as ImageView)
                        .setColorFilter(Color.WHITE)

                    (param.thisObject.getField("newDot") as ImageView)
                        .setColorFilter(Color.WHITE)

                    (param.thisObject.getField("text") as TextView)
                        .setTextColor(Color.WHITE)
                }

            footerActionsViewBinderClass
                .hookMethod("bind")
                .suppressError()
                .runAfter { param ->
                    if (!blackQSHeaderEnabled) return@runAfter

                    val view = param.args[0] as LinearLayout
                    view.setBackgroundColor(Color.BLACK)
                    view.elevation = 0f
                }
        } catch (ignored: Throwable) {
        }

        // Background color of android 14's charging chip. Fix for light QS theme situation
        val batteryStatusChipColorHook: XC_MethodHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (blackQSHeaderEnabled) {
                    (param.thisObject.getField("roundedContainer") as LinearLayout)
                        .background.setTint(Color.DKGRAY)

                    param.thisObject
                        .getField("batteryMeterView")
                        .callMethod(
                            "updateColors",
                            Color.WHITE,
                            Color.GRAY,
                            Color.WHITE
                        )
                }
            }
        }

        batteryStatusChipClass
            .hookConstructor()
            .run(batteryStatusChipColorHook)

        batteryStatusChipClass
            .hookMethod("onConfigurationChanged")
            .run(batteryStatusChipColorHook)

        qsPanelControllerClass
            .hookConstructor()
            .runAfter { calculateColors() }

        shadeHeaderControllerClass
            .hookMethod("onInit")
            .runAfter { param ->
                try {
                    val mView = param.thisObject.getField("mView") as View
                    val iconManager = param.thisObject.getField("iconManager")
                    val batteryIcon = param.thisObject.getField("batteryIcon")
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
                    log(this@QSBlackThemeA15, throwable)
                }
            }

        // A14 ap11 onwards - modern implementation of mobile icons
        val shadeCarrierGroupControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.shade.carrier.ShadeCarrierGroupController",
            suppressError = true
        )
        val mobileIconBinderClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.pipeline.mobile.ui.binder.MobileIconBinder",
            suppressError = true
        )

        shadeCarrierGroupControllerClass
            .hookConstructor()
            .runAfter { param -> shadeCarrierGroupController = param.thisObject }

        mobileIconBinderClass
            .hookMethod("bind")
            .runAfter { param ->
                if (param.args[1].javaClass.name.contains("ShadeCarrierGroupMobileIconViewModel")
                ) {
                    modernShadeCarrierGroupMobileViews.add(param.result)

                    if (blackQSHeaderEnabled) {
                        setMobileIconTint(param.result, Color.WHITE)
                    }
                }
            }

        qsContainerImplClass
            .hookMethod("updateResources")
            .suppressError()
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    val view = (param.thisObject as ViewGroup).findViewById<ViewGroup>(
                        mContext.resources.getIdentifier(
                            "qs_footer_actions",
                            "id",
                            mContext.packageName
                        )
                    ).also {
                        it.background?.setTint(Color.BLACK)
                        it.elevation = 0f
                    }

                    // Settings button
                    view.findViewById<View>(
                        mContext.resources.getIdentifier(
                            "settings_button_container",
                            "id",
                            mContext.packageName
                        )
                    ).findViewById<ImageView>(
                        mContext.resources.getIdentifier(
                            "icon",
                            "id",
                            mContext.packageName
                        )
                    ).imageTintList = ColorStateList.valueOf(Color.WHITE)

                    // Power menu button
                    try {
                        view.findViewById<ImageView?>(
                            mContext.resources.getIdentifier(
                                "pm_lite",
                                "id",
                                mContext.packageName
                            )
                        )
                    } catch (ignored: ClassCastException) {
                        view.findViewById<ViewGroup?>(
                            mContext.resources.getIdentifier(
                                "pm_lite",
                                "id",
                                mContext.packageName
                            )
                        )
                    }?.apply {
                        if (this is ImageView) {
                            imageTintList = ColorStateList.valueOf(Color.BLACK)
                        } else if (this is ViewGroup) {
                            (getChildAt(0) as ImageView).setColorFilter(
                                Color.WHITE,
                                PorterDuff.Mode.SRC_IN
                            )
                        }
                    }
                } catch (ignored: Throwable) {
                    // it will fail on compose implementation
                }
            }

        // QS Customize panel
        qsCustomizerClass
            .hookConstructor()
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                val mainView = param.thisObject as ViewGroup

                for (i in 0 until mainView.childCount) {
                    mainView.getChildAt(i).setBackgroundColor(Color.BLACK)
                }
            }

        // Mobile signal icons - this is the legacy model. new model uses viewmodels
        shadeCarrierClass
            .hookMethod("updateState")
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                (param.thisObject.getField("mMobileSignal") as ImageView).imageTintList =
                    ColorStateList.valueOf(Color.WHITE)
            }

        //QS Footer built text row
        qsFooterViewClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    (param.thisObject.getField("mBuildText") as TextView)
                        .setTextColor(Color.WHITE)
                } catch (ignored: Throwable) {
                }

                try {
                    (param.thisObject.getField("mEditButton") as ImageView)
                        .setColorFilter(Color.WHITE)
                } catch (ignored: Throwable) {
                }

                try {
                    param.thisObject.getField("mPageIndicator").setField(
                        "mTint",
                        ColorStateList.valueOf(Color.WHITE)
                    )
                } catch (ignored: Throwable) {
                }
            }

        // QS tile primary label color
        qsTileViewImplClass
            .hookMethod("getLabelColorForState")
            .suppressError()
            .runBefore { param ->
                if (!blackQSHeaderEnabled) return@runBefore

                try {
                    if (param.args[0] as Int == Tile.STATE_ACTIVE) {
                        param.result = colorText
                    }
                } catch (throwable: Throwable) {
                    log(this@QSBlackThemeA15, throwable)
                }
            }

        // QS tile secondary label color
        qsTileViewImplClass
            .hookMethod("getSecondaryLabelColorForState")
            .suppressError()
            .runBefore { param ->
                if (!blackQSHeaderEnabled) return@runBefore

                try {
                    if (param.args[0] as Int == Tile.STATE_ACTIVE) {
                        param.result = colorText
                    }
                } catch (throwable: Throwable) {
                    log(this@QSBlackThemeA15, throwable)
                }
            }

        // Auto Brightness Icon Color
        brightnessControllerClass
            .hookMethod("updateIcon")
            .suppressError()
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    val iconColor = if ((param.args?.get(0) ?: false) as Boolean) {
                        Color.BLACK
                    } else {
                        Color.WHITE
                    }
                    val mIcon = param.thisObject.getField("mIcon") as ImageView

                    mIcon.imageTintList = ColorStateList.valueOf(iconColor)
                } catch (throwable: Throwable) {
                    log(this@QSBlackThemeA15, throwable)
                }
            }

        brightnessSliderControllerClass
            .hookConstructor()
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    (param.thisObject.getField("mIcon") as ImageView).imageTintList =
                        ColorStateList.valueOf(colorText!!)
                } catch (throwable: Throwable) {
                    try {
                        (param.thisObject.getField(
                            "mIconView"
                        ) as ImageView).imageTintList =
                            ColorStateList.valueOf(colorText!!)
                    } catch (ignored: Throwable) {
                    }
                }
            }

        brightnessMirrorControllerClass
            .hookMethod("updateIcon")
            .suppressError()
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    val mIcon = param.thisObject.getField("mIcon") as ImageView
                    mIcon.imageTintList = ColorStateList.valueOf(colorText!!)
                } catch (throwable: Throwable) {
                    log(this@QSBlackThemeA15, throwable)
                }
            }

        qsIconViewImplClass
            .hookMethod("setIcon")
            .runBefore { param ->
                if (!blackQSHeaderEnabled) return@runBefore

                try {
                    if (param.args[0] is ImageView &&
                        param.args[1].getField("state") as Int == Tile.STATE_ACTIVE
                    ) {
                        param.thisObject.setField("mTint", colorText)
                    }
                } catch (throwable: Throwable) {
                    log(this@QSBlackThemeA15, throwable)
                }
            }

        // Black QS Clock bug
        quickStatusBarHeaderClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                try {
                    mClockViewQSHeader = param.thisObject.getField("mClockView")
                } catch (ignored: Throwable) {
                }

                if (blackQSHeaderEnabled && mClockViewQSHeader != null) {
                    try {
                        (mClockViewQSHeader as TextView).setTextColor(Color.WHITE)
                    } catch (throwable: Throwable) {
                        log(this@QSBlackThemeA15, throwable)
                    }
                }
            }

        // Black QS Clock bug
        clockClass
            .hookMethod("onColorsChanged")
            .suppressError()
            .runAfter {
                if (!blackQSHeaderEnabled) return@runAfter

                (mClockViewQSHeader as? TextView)?.setTextColor(Color.WHITE)
            }

        centralSurfacesImplClass
            .hookConstructor()
            .runAfter { initColors(true) }

        centralSurfacesImplClass
            .hookMethod("updateTheme")
            .runAfter { initColors(false) }

        // Composition makes it almost impossible to control icon color via reflection
        qsIconViewImplClass
            .hookConstructor()
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                val originalIcon = param.thisObject.getField("mIcon") as ImageView
                val replacementIcon = TintControlledImageView(originalIcon.context).apply {
                    setImageDrawable(originalIcon.drawable)
                    id = mContext.resources.getIdentifier(
                        "icon",
                        "id",
                        mContext.packageName
                    )
                }

                param.thisObject.setField("mIcon", replacementIcon)

                (param.thisObject as ViewGroup).apply {
                    val index = indexOfChild(originalIcon)
                    removeView(originalIcon)
                    addView(replacementIcon, index)
                }
            }

        qsTileViewImplClass
            .hookConstructor()
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    mPrimaryLabelActiveColor = ColorStateList.valueOf(
                        param.thisObject.getField("colorLabelActive") as Int
                    )
                    mSecondaryLabelActiveColor = ColorStateList.valueOf(
                        param.thisObject.getField("colorSecondaryLabelActive") as Int
                    )
                    mPrimaryLabelInactiveColor = ColorStateList.valueOf(
                        param.thisObject.getField("colorLabelInactive") as Int
                    )
                    mSecondaryLabelInactiveColor = ColorStateList.valueOf(
                        param.thisObject.getField("colorSecondaryLabelInactive") as Int
                    )

                    if (!customQsTextColor) {
                        param.thisObject.setField("colorLabelActive", colorText)
                        param.thisObject.setField(
                            "colorSecondaryLabelActive",
                            colorTextAlpha
                        )
                    }

                    param.thisObject.setField("colorLabelInactive", Color.WHITE)
                    param.thisObject.setField(
                        "colorSecondaryLabelInactive",
                        -0x7f000001
                    )

                    val sideView = param.thisObject.getField("sideView") as ViewGroup

                    val customDrawable = sideView.findViewById<ImageView>(
                        mContext.resources.getIdentifier(
                            "customDrawable",
                            "id",
                            mContext.packageName
                        )
                    )
                    customDrawable.imageTintList = ColorStateList.valueOf(colorText!!)

                    val chevron = sideView.findViewById<ImageView>(
                        mContext.resources.getIdentifier(
                            "chevron",
                            "id",
                            mContext.packageName
                        )
                    )
                    chevron.imageTintList = ColorStateList.valueOf(colorText!!)
                } catch (throwable: Throwable) {
                    log(this@QSBlackThemeA15, throwable)
                }
            }

        qsIconViewImplClass
            .hookMethod("getIconColorForState", "getColor")
            .runBefore { param ->
                if (!blackQSHeaderEnabled) return@runBefore

                val (isDisabledState: Boolean,
                    isActiveState: Boolean) = Utils.getTileState(param)

                if (isDisabledState) {
                    param.result = -0x7f000001
                } else if (isActiveState && !customQsTextColor) {
                    param.result = colorText
                } else if (!isActiveState) {
                    param.result = Color.WHITE
                }
            }

        qsIconViewImplClass
            .hookMethod("updateIcon")
            .runAfter { param ->
                if (!blackQSHeaderEnabled || customQsTextColor) return@runAfter

                val (isDisabledState: Boolean,
                    isActiveState: Boolean) = Utils.getTileState(param)

                val mIcon = param.args[0] as ImageView

                if (isDisabledState) {
                    mIcon.imageTintList = ColorStateList.valueOf(-0x7f000001)
                } else if (isActiveState && !customQsTextColor) {
                    mIcon.imageTintList = ColorStateList.valueOf(colorText!!)
                } else if (!isActiveState) {
                    mIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
                }
            }

        qsContainerImplClass
            .hookMethod("updateResources")
            .suppressError()
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    val res = mContext.resources
                    val view = (param.thisObject as ViewGroup).findViewById<ViewGroup>(
                        res.getIdentifier(
                            "qs_footer_actions",
                            "id",
                            mContext.packageName
                        )
                    )

                    try {
                        val pmButtonContainer = view.findViewById<ViewGroup>(
                            res.getIdentifier(
                                "pm_lite",
                                "id",
                                mContext.packageName
                            )
                        )

                        (pmButtonContainer.getChildAt(0) as ImageView).setColorFilter(
                            Color.BLACK,
                            PorterDuff.Mode.SRC_IN
                        )
                    } catch (ignored: Throwable) {
                        val pmButton = view.findViewById<ImageView>(
                            res.getIdentifier(
                                "pm_lite",
                                "id",
                                mContext.packageName
                            )
                        )

                        pmButton.imageTintList = ColorStateList.valueOf(Color.BLACK)
                    }
                } catch (ignored: Throwable) {
                }
            }

        // Compose implementation of QS Footer actions
        val graphicsColorKtClass = findClass(
            "androidx.compose.ui.graphics.ColorKt",
            suppressError = true
        )

        expandableControllerImplClass
            .hookConstructor()
            .runBefore { param ->
                if (!blackQSHeaderEnabled) return@runBefore

                param.args[1] = callStaticMethod(graphicsColorKtClass, "Color", Color.WHITE)
            }

        val colorAttrParams = themeColorKtClass?.let {
            it.findMethod("colorAttr")?.parameters
        } ?: emptyArray()
        val resIdIndex = colorAttrParams.indexOfFirst {
            it.type == Int::class.javaPrimitiveType
        }.takeIf { it != -1 } ?: 0

        themeColorKtClass
            .hookMethod("colorAttr")
            .runBefore { param ->
                if (!blackQSHeaderEnabled) return@runBefore

                val code = param.args[resIdIndex] as Int
                var result = 0

                if (code == PM_LITE_BACKGROUND_CODE) {
                    if (colorActive != null) {
                        result = colorActive!!
                    }
                } else {
                    try {
                        when (mContext.resources.getResourceName(code).split("/")[1]) {
                            "underSurface", "onShadeActive", "shadeInactive" -> {
                                if (colorInactive != null) {
                                    result = colorInactive!! // button backgrounds
                                }
                            }

                            "onShadeInactiveVariant" -> {
                                result = Color.WHITE // "number button" text
                            }
                        }
                    } catch (ignored: Throwable) {
                    }
                }

                if (result != 0) {
                    param.result = callStaticMethod(graphicsColorKtClass, "Color", result)
                }
            }

        footerActionsViewModelClass
            .hookConstructor()
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                // Power button
                val power = param.thisObject.getField("power")
                power.setField("iconTint", Color.BLACK)
                power.setField("backgroundColor", PM_LITE_BACKGROUND_CODE)

                // Settings button
                val settings = param.thisObject.getField("settings")
                settings.setField("iconTint", Color.WHITE)

                // We must use the classes defined in the apk. Using our own will fail.
                val stateFlowImplClass = findClass("kotlinx.coroutines.flow.StateFlowImpl")!!
                val readonlyStateFlowClass =
                    findClass("kotlinx.coroutines.flow.ReadonlyStateFlow")!!

                try {
                    val zeroAlphaFlow = stateFlowImplClass
                        .getConstructor(Any::class.java)
                        .newInstance(0f)

                    val readonlyStateFlowInstance = try {
                        readonlyStateFlowClass.constructors[0].newInstance(zeroAlphaFlow)
                    } catch (ignored: Throwable) {
                        readonlyStateFlowClass.constructors[0].newInstance(zeroAlphaFlow, null)
                    }

                    param.thisObject.setField(
                        "backgroundAlpha",
                        readonlyStateFlowInstance
                    )
                } catch (throwable: Throwable) {
                    log(this@QSBlackThemeA15, throwable)
                }
            }

        try {
            mBehindColors = gradientColorsClass.getDeclaredConstructor().newInstance()
        } catch (throwable: Throwable) {
            log(this@QSBlackThemeA15, throwable)
        }

        scrimControllerClass
            .hookMethod("updateScrims")
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

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
                    log(this@QSBlackThemeA15, throwable)
                }
            }

        scrimControllerClass
            .hookMethod("updateThemeColors")
            .runAfter {
                calculateColors()

                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    val states: ColorStateList = getColorAttr(
                        mContext.resources.getIdentifier(
                            "android:attr/colorBackgroundFloating",
                            "attr",
                            mContext.packageName
                        ), mContext
                    )
                    val surfaceBackground = states.defaultColor
                    val accentStates: ColorStateList = getColorAttr(
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
                    log(this@QSBlackThemeA15, throwable)
                }
            }

        scrimControllerClass
            .hookMethodMatchPattern("applyState.*")
            .runAfter { param ->
                if (!blackQSHeaderEnabled) return@runAfter

                try {
                    val mClipsQsScrim =
                        param.thisObject.getField("mClipsQsScrim") as Boolean
                    if (mClipsQsScrim) {
                        param.thisObject.setField("mBehindTint", Color.BLACK)
                    }
                } catch (throwable: Throwable) {
                    log(this@QSBlackThemeA15, throwable)
                }
            }

        val constants: Array<out Any> = scrimStateEnum.enumConstants ?: arrayOf()
        constants.forEach { constant ->
            when (constant.toString()) {
                "KEYGUARD" -> constant.javaClass
                    .hookMethod("prepare")
                    .runAfter { param ->
                        if (!blackQSHeaderEnabled) return@runAfter

                        val mClipQsScrim = param.thisObject.getField(
                            "mClipQsScrim"
                        ) as Boolean

                        if (mClipQsScrim) {
                            val mScrimBehind = param.thisObject.getField(
                                "mScrimBehind"
                            )
                            val mTintColor = mScrimBehind.getField("mTintColor") as Int

                            if (mTintColor != Color.BLACK) {
                                mScrimBehind.setField(
                                    "mTintColor",
                                    Color.BLACK
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
                        if (!blackQSHeaderEnabled) return@runAfter

                        param.thisObject.setField("mBehindTint", Color.BLACK)
                    }

                "SHADE_LOCKED" -> {
                    constant.javaClass
                        .hookMethod("prepare")
                        .runAfter { param ->
                            if (!blackQSHeaderEnabled) return@runAfter

                            param.thisObject.setField(
                                "mBehindTint",
                                Color.BLACK
                            )

                            val mClipQsScrim = param.thisObject.getField(
                                "mClipQsScrim"
                            ) as Boolean

                            if (mClipQsScrim) {
                                val mScrimBehind = param.thisObject.getField(
                                    "mScrimBehind"
                                )
                                val mTintColor = mScrimBehind.getField("mTintColor") as Int

                                if (mTintColor != Color.BLACK) {
                                    mScrimBehind.setField(
                                        "mTintColor",
                                        Color.BLACK
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
                            if (!blackQSHeaderEnabled) return@runBefore

                            param.result = Color.BLACK
                        }
                }

                "UNLOCKED" -> constant.javaClass
                    .hookMethod("prepare")
                    .runAfter { param ->
                        if (!blackQSHeaderEnabled) return@runAfter

                        param.thisObject.setField(
                            "mBehindTint",
                            Color.BLACK
                        )

                        val mScrimBehind = param.thisObject.getField("mScrimBehind")
                        val mTintColor = mScrimBehind.getField("mTintColor") as Int

                        if (mTintColor != Color.BLACK) {
                            mScrimBehind.setField(
                                "mTintColor",
                                Color.BLACK
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
                    log(this@QSBlackThemeA15, throwable)
                }
            }
    }

    private fun initColors(force: Boolean) {
        val isDark: Boolean = SystemUtils.isDarkMode
        if (isDark == this.isDark && !force) return

        this.isDark = isDark

        calculateColors()
    }

    private fun calculateColors() {
        try {
            colorActive = mContext.resources.getColor(
                mContext.resources.getIdentifier(
                    "android:color/system_accent1_100",
                    "color",
                    mContext.packageName
                ), mContext.theme
            )

            colorInactive = mContext.resources.getColor(
                mContext.resources.getIdentifier(
                    "android:color/system_neutral2_800",
                    "color",
                    mContext.packageName
                ), mContext.theme
            )

            colorText = mContext.resources.getColor(
                mContext.resources.getIdentifier(
                    "android:color/system_neutral1_900",
                    "color",
                    mContext.packageName
                ), mContext.theme
            )

            colorTextAlpha = colorText!! and 0xFFFFFF or (Math.round(
                Color.alpha(
                    colorText!!
                ) * 0.8f
            ) shl 24)
        } catch (throwable: Throwable) {
            log(this@QSBlackThemeA15, throwable)
        }
    }

    private fun setHeaderComponentsColor(
        mView: View,
        iconManager: Any,
        batteryIcon: Any
    ) {
        if (!blackQSHeaderEnabled) return

        val textColor = Color.WHITE

        try {
            (mView.findViewById<View>(
                mContext.resources.getIdentifier(
                    "clock",
                    "id",
                    mContext.packageName
                )
            ) as TextView).setTextColor(textColor)

            (mView.findViewById<View>(
                mContext.resources.getIdentifier(
                    "date",
                    "id",
                    mContext.packageName
                )
            ) as TextView).setTextColor(textColor)
        } catch (ignored: Throwable) {
        }

        try {
            try { // A14 ap11
                iconManager.callMethod("setTint", textColor, textColor)

                modernShadeCarrierGroupMobileViews.forEach { view ->
                    setMobileIconTint(
                        view,
                        textColor
                    )
                }

                setModernSignalTextColor(textColor)
            } catch (ignored: Throwable) { // A14 older
                iconManager.callMethod("setTint", textColor)
            }

            for (i in 1..3) {
                try {
                    (mView.findViewById<View>(
                            mContext.resources.getIdentifier(
                                "carrier$i",
                                "id",
                                mContext.packageName
                            )
                    ).getField("mCarrierText") as TextView).setTextColor(textColor)

                    (mView.findViewById<View>(
                            mContext.resources.getIdentifier(
                                "carrier$i",
                                "id",
                                mContext.packageName
                            )
                    ).getField("mMobileSignal") as ImageView).imageTintList =
                        ColorStateList.valueOf(textColor)

                    (mView.findViewById<View>(
                            mContext.resources.getIdentifier(
                                "carrier$i",
                                "id",
                                mContext.packageName
                            )
                    ).getField("mMobileRoaming") as ImageView).imageTintList =
                        ColorStateList.valueOf(textColor)
                } catch (ignored: Throwable) {
                }
            }

            batteryIcon.callMethod("updateColors", textColor, textColor, textColor)
        } catch (throwable: Throwable) {
            log(this@QSBlackThemeA15, throwable)
        }
    }

    private fun setMobileIconTint(modernStatusBarViewBinding: Any, textColor: Int) {
        modernStatusBarViewBinding.callMethod(
            "onIconTintChanged",
            textColor,
            textColor
        )
    }

    @Suppress("UNCHECKED_CAST", "SameParameterValue")
    private fun setModernSignalTextColor(textColor: Int) {
        val res: Resources = mContext.resources
        if (shadeCarrierGroupController == null) return

        val carrierGroups =
            shadeCarrierGroupController.getField("mCarrierGroups") as Array<View>?

        carrierGroups?.let {
            for (shadeCarrier in it) {
                try {
                    shadeCarrier.findViewById<View>(
                        res.getIdentifier(
                            "carrier_combo",
                            "id",
                            mContext.packageName
                        )
                    )?.findViewById<TextView>(
                        res.getIdentifier(
                            "mobile_carrier_text",
                            "id",
                            mContext.packageName
                        )
                    )?.setTextColor(textColor)
                } catch (ignored: Throwable) {
                }
            }
        }
    }

    @SuppressLint("AppCompatCustomView")
    inner class TintControlledImageView : ImageView {

        constructor(context: Context?) : super(context)

        constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

        constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
            context,
            attrs,
            defStyleAttr
        )

        override fun setImageTintList(tintList: ColorStateList?) {
            super.setImageTintList(getIconTintLightMode(tintList))
        }

        private fun getIconTintLightMode(tintList: ColorStateList?): ColorStateList {
            return when (tintList) {
                mPrimaryLabelActiveColor -> ColorStateList.valueOf(colorText ?: Color.BLACK)
                mSecondaryLabelActiveColor -> ColorStateList.valueOf(colorTextAlpha ?: -0x80000000)
                mPrimaryLabelInactiveColor -> ColorStateList.valueOf(Color.WHITE)
                mSecondaryLabelInactiveColor -> ColorStateList.valueOf(-0x7f000001)
                else -> tintList ?: ColorStateList.valueOf(Color.WHITE)
            }
        }
    }

    companion object {
        private var blackQSHeaderEnabled = false
        private const val PM_LITE_BACKGROUND_CODE = 1
    }
}