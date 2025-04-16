package com.drdisagree.iconify.xposed.modules.quicksettings.themes

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.service.quicksettings.Tile
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.common.Preferences.FLUID_NOTIF_TRANSPARENCY
import com.drdisagree.iconify.data.common.Preferences.FLUID_POWERMENU_TRANSPARENCY
import com.drdisagree.iconify.data.common.Preferences.FLUID_QSPANEL
import com.drdisagree.iconify.xposed.HookRes
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.SettingsLibUtils
import com.drdisagree.iconify.xposed.modules.extras.utils.ViewHelper.toPx
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getFieldSilently
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethodMatchPattern
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setField
import com.drdisagree.iconify.xposed.modules.extras.views.RoundedCornerProgressDrawable
import com.drdisagree.iconify.xposed.utils.SystemUtils
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlin.math.max
import kotlin.math.min

@SuppressLint("DiscouragedApi")
class QSFluidThemeA14(context: Context) : ModPack(context) {

    private var wasDark: Boolean = SystemUtils.isDarkMode
    private var mSlider: SeekBar? = null
    private var colorActive = mContext.resources.getColor(
        mContext.resources.getIdentifier(
            "android:color/system_accent1_400",
            "color",
            mContext.packageName
        ), mContext.theme
    )
    private var colorInactive = SettingsLibUtils.getColorAttrDefaultColor(
        mContext,
        mContext.resources.getIdentifier(
            "offStateColor",
            "attr",
            mContext.packageName
        )
    )
    private var colorActiveAlpha = changeAlpha(colorActive, ACTIVE_ALPHA)
    private var colorInactiveAlpha = changeAlpha(colorInactive, INACTIVE_ALPHA)

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            fluidQsThemeEnabled = getBoolean(FLUID_QSPANEL, false)
            fluidNotificationEnabled = fluidQsThemeEnabled &&
                    getBoolean(FLUID_NOTIF_TRANSPARENCY, false)
            fluidPowerMenuEnabled = fluidQsThemeEnabled &&
                    getBoolean(FLUID_POWERMENU_TRANSPARENCY, false)
        }

        initResources()
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val qsPanelClass = findClass("$SYSTEMUI_PACKAGE.qs.QSPanel")
        val qsTileViewImplClass = findClass("$SYSTEMUI_PACKAGE.qs.tileimpl.QSTileViewImpl")
        val qsIconViewImplClass = findClass("$SYSTEMUI_PACKAGE.qs.tileimpl.QSIconViewImpl")
        val footerViewClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.notification.footer.ui.view.FooterView",
            "$SYSTEMUI_PACKAGE.statusbar.notification.row.FooterView"
        )
        val centralSurfacesImplClass = findClass(
            "$SYSTEMUI_PACKAGE.statusbar.phone.CentralSurfacesImpl",
            suppressError = true
        )
        val notificationExpandButtonClass = findClass(
            "com.android.internal.widget.NotificationExpandButton",
            suppressError = true
        )
        val brightnessSliderViewClass =
            findClass("$SYSTEMUI_PACKAGE.settings.brightness.BrightnessSliderView")
        val brightnessControllerClass =
            findClass("$SYSTEMUI_PACKAGE.settings.brightness.BrightnessController")
        val brightnessMirrorControllerClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.policy.BrightnessMirrorController")
        val brightnessSliderControllerClass = findClass(
            "$SYSTEMUI_PACKAGE.settings.brightness.BrightnessSliderController",
            suppressError = true
        )
        val activatableNotificationViewClass =
            findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.ActivatableNotificationView")
        val themeColorKtClass = findClass(
            "com.android.compose.theme.ColorKt",
            suppressError = true
        )
        val footerActionsViewModelClass =
            findClass("$SYSTEMUI_PACKAGE.qs.footer.ui.viewmodel.FooterActionsViewModel")
        val footerActionsViewBinderClass = findClass(
            "$SYSTEMUI_PACKAGE.qs.footer.ui.binder.FooterActionsViewBinder",
            suppressError = true
        )

        // Initialize resources and colors
        qsTileViewImplClass
            .hookMethod("init")
            .runBefore { initResources() }

        centralSurfacesImplClass
            .hookConstructor()
            .runBefore { initResources() }

        centralSurfacesImplClass
            .hookMethod("updateTheme")
            .runBefore { initResources() }

        qsTileViewImplClass
            .hookConstructor()
            .runAfter {
                val tempColorInactive = SettingsLibUtils.getColorAttrDefaultColor(
                    mContext,
                    mContext.resources.getIdentifier(
                        "offStateColor",
                        "attr",
                        mContext.packageName
                    )
                )

                colorInactive = if (tempColorInactive != 0) tempColorInactive
                else SettingsLibUtils.getColorAttrDefaultColor(
                    mContext,
                    mContext.resources.getIdentifier(
                        "shadeInactive",
                        "attr",
                        mContext.packageName
                    )
                )
                colorInactiveAlpha = changeAlpha(colorInactive, INACTIVE_ALPHA)
            }

        qsTileViewImplClass
            .hookMethod("getBackgroundColorForState")
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                try {
                    if (param.args[0] as Int == Tile.STATE_ACTIVE) {
                        param.result = colorActiveAlpha
                    } else {
                        val inactiveColor = param.result as Int?

                        inactiveColor?.let {
                            colorInactive = it
                            colorInactiveAlpha = changeAlpha(it, INACTIVE_ALPHA)

                            if (param.args[0] as Int == Tile.STATE_INACTIVE) {
                                param.result = changeAlpha(it, INACTIVE_ALPHA)
                            } else if (param.args[0] as Int == Tile.STATE_UNAVAILABLE) {
                                param.result = changeAlpha(it, UNAVAILABLE_ALPHA)
                            }
                        }
                    }
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA14, throwable)
                }
            }

        // QS icon color
        qsIconViewImplClass
            .hookMethod("getIconColorForState", "getColor")
            .runBefore { param ->
                if (!fluidQsThemeEnabled) return@runBefore

                try {
                    if (param.args[1].getField("state") as Int == Tile.STATE_ACTIVE) {
                        param.result = colorActive
                    }
                } catch (ignored: Throwable) {
                }
            }

        qsIconViewImplClass
            .hookMethod("updateIcon")
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                try {
                    if (param.args[0] is ImageView &&
                        param.args[1].getField("state") as Int == Tile.STATE_ACTIVE
                    ) {
                        (param.args[0] as ImageView).imageTintList = ColorStateList.valueOf(
                            colorActive
                        )
                    }
                } catch (ignored: Throwable) {
                }
            }

        qsIconViewImplClass
            .hookMethod("setIcon")
            .runBefore { param ->
                if (!fluidQsThemeEnabled) return@runBefore

                try {
                    if (param.args[0] is ImageView &&
                        param.args[1].getField("state") as Int == Tile.STATE_ACTIVE
                    ) {
                        param.thisObject.setField("mTint", colorActive)
                    }
                } catch (ignored: Throwable) {
                }
            }

        val qsContainerImplClass = findClass("$SYSTEMUI_PACKAGE.qs.QSContainerImpl")

        qsContainerImplClass
            .hookMethod("updateResources")
            .suppressError()
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                val view = (param.thisObject as ViewGroup).findViewById<ViewGroup>(
                    mContext.resources.getIdentifier(
                        "qs_footer_actions",
                        "id",
                        mContext.packageName
                    )
                ).also {
                    it.background?.setTint(Color.TRANSPARENT)
                    it.elevation = 0f
                }

                // Security footer
                view.let {
                    it.getChildAt(0)?.apply {
                        background?.setTint(colorInactiveAlpha)
                        background?.alpha = (INACTIVE_ALPHA * 255).toInt()
                    }
                    it.getChildAt(1)?.apply {
                        background?.setTint(colorInactiveAlpha)
                        background?.alpha = (INACTIVE_ALPHA * 255).toInt()
                    }
                }

                // Settings button
                view.findViewById<View?>(
                    mContext.resources.getIdentifier(
                        "settings_button_container",
                        "id",
                        mContext.packageName
                    )
                )?.apply {
                    background.setTint(colorInactiveAlpha)
                }

                // Multi user switch
                view.findViewById<View?>(
                    mContext.resources.getIdentifier(
                        "multi_user_switch",
                        "id",
                        mContext.packageName
                    )
                )?.apply {
                    background.setTint(colorInactiveAlpha)
                }

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
                    background.setTint(colorActive)
                    background.alpha = (ACTIVE_ALPHA * 255).toInt()

                    if (this is ImageView) {
                        imageTintList = ColorStateList.valueOf(colorActive)
                    } else if (this is ViewGroup) {
                        (getChildAt(0) as ImageView).setColorFilter(
                            colorActive,
                            PorterDuff.Mode.SRC_IN
                        )
                    }
                }
            }

        // Compose implementation of QS Footer actions
        val graphicsColorKtClass = findClass(
            "androidx.compose.ui.graphics.ColorKt",
            suppressError = true
        )

        themeColorKtClass
            .hookMethod("colorAttr")
            .runBefore { param ->
                if (!fluidQsThemeEnabled) return@runBefore

                val code = param.args[0] as Int
                var result = 0

                when (code) {
                    PM_LITE_BACKGROUND_CODE -> {
                        result = colorActiveAlpha
                    }

                    else -> {
                        try {
                            when (mContext.resources.getResourceName(code).split("/")[1]) {
                                "underSurface", "onShadeActive", "shadeInactive" -> {
                                    result = colorInactiveAlpha // button backgrounds
                                }
                            }
                        } catch (ignored: Throwable) {
                        }
                    }
                }

                if (result != 0) {
                    param.result = callStaticMethod(graphicsColorKtClass, "Color", result)
                }
            }

        footerActionsViewModelClass
            .hookConstructor()
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                // Power button
                val power = param.thisObject.getField("power")
                power.setField("iconTint", colorActive)
                power.setField("backgroundColor", PM_LITE_BACKGROUND_CODE)

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
                    log(this@QSFluidThemeA14, throwable)
                }
            }

        footerActionsViewBinderClass
            .hookMethod("bindButton")
            .suppressError()
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                val view = param.args[0].getField("view") as View
                view.background?.alpha = (INACTIVE_ALPHA * 255).toInt()
            }

        footerActionsViewBinderClass
            .hookMethod("bind")
            .suppressError()
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                val view = param.args[0] as LinearLayout
                view.setBackgroundColor(Color.TRANSPARENT)
                view.elevation = 0f
            }

        // Brightness slider and auto brightness color
        brightnessSliderViewClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                mSlider = param.thisObject.getFieldSilently("mSlider") as? SeekBar
                    ?: return@runAfter

                if (!fluidQsThemeEnabled) return@runAfter

                try {
                    mSlider!!.progressDrawable = createBrightnessDrawable(mContext)

                    val progress = mSlider!!.progressDrawable as LayerDrawable
                    val progressSlider = progress
                        .findDrawableByLayerId(android.R.id.progress) as DrawableWrapper

                    try {
                        val actualProgressSlider = progressSlider.drawable as LayerDrawable?
                        val mBrightnessIcon = actualProgressSlider!!.findDrawableByLayerId(
                            mContext.resources.getIdentifier(
                                "slider_icon",
                                "id",
                                mContext.packageName
                            )
                        )

                        mBrightnessIcon.setTintList(ColorStateList.valueOf(Color.TRANSPARENT))
                        mBrightnessIcon.alpha = 0
                    } catch (ignored: Throwable) {
                    }
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA14, throwable)
                }
            }

        brightnessControllerClass
            .hookMethod("updateIcon")
            .suppressError()
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                try {
                    (param.thisObject.getField(
                        "mIcon"
                    ) as ImageView).imageTintList = ColorStateList.valueOf(
                        colorActive
                    )

                    (param.thisObject.getField(
                        "mIcon"
                    ) as ImageView).backgroundTintList = ColorStateList.valueOf(
                        colorActiveAlpha
                    )
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA14, throwable)
                }
            }

        brightnessSliderControllerClass
            .hookConstructor()
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                try {
                    (param.thisObject.getField(
                        "mIcon"
                    ) as ImageView).imageTintList = ColorStateList.valueOf(
                        colorActive
                    )

                    (param.thisObject.getField(
                        "mIcon"
                    ) as ImageView).backgroundTintList = ColorStateList.valueOf(
                        colorActiveAlpha
                    )
                } catch (throwable: Throwable) {
                    try {
                        (param.thisObject.getField(
                            "mIconView"
                        ) as ImageView).imageTintList = ColorStateList.valueOf(
                            colorActive
                        )

                        (param.thisObject.getField(
                            "mIconView"
                        ) as ImageView).backgroundTintList = ColorStateList.valueOf(
                            colorActiveAlpha
                        )
                    } catch (ignored: Throwable) {
                    }
                }
            }

        brightnessMirrorControllerClass
            .hookMethod("updateIcon")
            .suppressError()
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                try {
                    (param.thisObject.getField(
                        "mIcon"
                    ) as ImageView).imageTintList = ColorStateList.valueOf(
                        colorActive
                    )

                    (param.thisObject.getField(
                        "mIcon"
                    ) as ImageView).backgroundTintList = ColorStateList.valueOf(
                        colorActiveAlpha
                    )
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA14, throwable)
                }
            }

        brightnessMirrorControllerClass
            .hookMethod("updateResources")
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                try {
                    val mBrightnessMirror = param.thisObject.getField(
                        "mBrightnessMirror"
                    ) as FrameLayout
                    mBrightnessMirror.background.alpha = (INACTIVE_ALPHA * 255).toInt()
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA14, throwable)
                }
            }

        qsPanelClass
            .hookMethod("updateResources")
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                try {
                    (param.thisObject.getField("mAutoBrightnessView") as View)
                        .background.setTint(colorActiveAlpha)
                } catch (ignored: Throwable) {
                }
            }

        // QS tile primary label color
        qsTileViewImplClass
            .hookMethod("getLabelColorForState")
            .runBefore { param ->
                if (!fluidQsThemeEnabled) return@runBefore

                try {
                    if (param.args[0] as Int == Tile.STATE_ACTIVE) {
                        param.result = colorActive
                    }
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA14, throwable)
                }
            }

        // QS tile secondary label color
        qsTileViewImplClass
            .hookMethod("getSecondaryLabelColorForState")
            .runBefore { param ->
                if (!fluidQsThemeEnabled) return@runBefore

                try {
                    if (param.args[0] as Int == Tile.STATE_ACTIVE) {
                        param.result = colorActive
                    }
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA14, throwable)
                }
            }

        qsTileViewImplClass
            .hookConstructor()
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                colorInactive = changeAlpha(
                    param.thisObject.getField(
                        "colorInactive"
                    ) as Int, 1.0f
                )
                colorInactiveAlpha = changeAlpha(colorInactive, INACTIVE_ALPHA)

                initResources()

                // For LineageOS based roms
                try {
                    param.thisObject.setField(
                        "colorActive",
                        colorActiveAlpha
                    )

                    param.thisObject.setField(
                        "colorInactive",
                        changeAlpha(
                            param.thisObject.getField("colorInactive") as Int,
                            INACTIVE_ALPHA
                        )
                    )

                    param.thisObject.setField(
                        "colorUnavailable",
                        changeAlpha(
                            param.thisObject.getField("colorInactive") as Int,
                            UNAVAILABLE_ALPHA
                        )
                    )

                    param.thisObject.setField(
                        "colorLabelActive",
                        colorActive
                    )

                    param.thisObject.setField(
                        "colorSecondaryLabelActive",
                        colorActive
                    )
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA14, throwable)
                }

                try {
                    if (mSlider != null) {
                        mSlider!!.progressDrawable = createBrightnessDrawable(mContext)

                        val progress = mSlider!!.progressDrawable as LayerDrawable
                        val progressSlider =
                            progress.findDrawableByLayerId(android.R.id.progress) as DrawableWrapper

                        try {
                            val actualProgressSlider = progressSlider.drawable as LayerDrawable?
                            val mBrightnessIcon = actualProgressSlider!!.findDrawableByLayerId(
                                mContext.resources.getIdentifier(
                                    "slider_icon",
                                    "id",
                                    mContext.packageName
                                )
                            )

                            mBrightnessIcon.setTintList(ColorStateList.valueOf(Color.TRANSPARENT))
                            mBrightnessIcon.alpha = 0
                        } catch (ignored: Throwable) {
                        }
                    }
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA14, throwable)
                }
            }

        qsTileViewImplClass
            .hookMethod("updateResources")
            .suppressError()
            .runBefore { param ->
                if (!fluidQsThemeEnabled) return@runBefore

                colorInactive = changeAlpha(
                    param.thisObject.getField(
                        "colorInactive"
                    ) as Int, 1.0f
                )
                colorInactiveAlpha = changeAlpha(colorInactive, INACTIVE_ALPHA)

                initResources()

                try {
                    param.thisObject.setField(
                        "colorActive",
                        colorActiveAlpha
                    )

                    param.thisObject.setField(
                        "colorInactive",
                        changeAlpha(
                            param.thisObject.getField("colorInactive") as Int,
                            INACTIVE_ALPHA
                        )
                    )

                    param.thisObject.setField(
                        "colorUnavailable",
                        changeAlpha(
                            param.thisObject.getField("colorInactive") as Int,
                            UNAVAILABLE_ALPHA
                        )
                    )

                    param.thisObject.setField(
                        "colorLabelActive",
                        colorActive
                    )

                    param.thisObject.setField(
                        "colorSecondaryLabelActive",
                        colorActive
                    )
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA14, throwable)
                }
            }

        // Notifications
        activatableNotificationViewClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                if (!fluidQsThemeEnabled || !fluidNotificationEnabled) return@runAfter

                val mBackgroundNormal =
                    param.thisObject.getField("mBackgroundNormal") as View?
                mBackgroundNormal?.alpha = INACTIVE_ALPHA
            }

        // Notification expand/collapse pill
        notificationExpandButtonClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                if (!fluidQsThemeEnabled || !fluidNotificationEnabled) return@runAfter

                val mPillView = (param.thisObject as ViewGroup).findViewById<View?>(
                    mContext.resources.getIdentifier(
                        "expand_button_pill",
                        "id",
                        mContext.packageName
                    )
                )
                mPillView?.background?.alpha = (INACTIVE_ALPHA * 255).toInt()
            }

        // Notification footer buttons
        val updateNotificationFooterButtons: XC_MethodHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!fluidQsThemeEnabled || !fluidNotificationEnabled) return

                try {
                    val mManageButton: Button = try {
                        param.thisObject.getField("mManageButton")
                    } catch (ignored: Throwable) {
                        param.thisObject.getField("mManageOrHistoryButton")
                    } as Button
                    val mClearAllButton: Button = try {
                        param.thisObject.getField("mClearAllButton")
                    } catch (ignored: Throwable) {
                        param.thisObject.getField("mDismissButton")
                    } as Button

                    mManageButton.background?.alpha = (INACTIVE_ALPHA * 255).toInt()
                    mClearAllButton.background?.alpha = (INACTIVE_ALPHA * 255).toInt()
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA14, throwable)
                }
            }
        }

        footerViewClass
            .hookMethod("onFinishInflate")
            .run(updateNotificationFooterButtons)

        footerViewClass
            .hookMethodMatchPattern("updateColors.*")
            .run(updateNotificationFooterButtons)

        // Power menu
        val globalActionsDialogLiteSinglePressActionClass = findClass(
            "$SYSTEMUI_PACKAGE.globalactions.GlobalActionsDialogLite\$SinglePressAction",
            suppressError = true
        )
        val globalActionsLayoutLiteClass = findClass(
            "$SYSTEMUI_PACKAGE.globalactions.GlobalActionsLayoutLite",
            suppressError = true
        )

        // Layout background
        globalActionsLayoutLiteClass
            .hookMethod("onLayout")
            .runBefore { param ->
                if (!fluidPowerMenuEnabled) return@runBefore

                (param.thisObject as View).findViewById<View>(android.R.id.list)
                    .background.alpha = (INACTIVE_ALPHA * 255).toInt()
            }

        // Button Color
        globalActionsDialogLiteSinglePressActionClass
            .hookMethod("create")
            .runAfter { param ->
                if (!fluidPowerMenuEnabled) return@runAfter

                val itemView = param.result as View
                val iconView = itemView.findViewById<ImageView>(android.R.id.icon)
                iconView.background.alpha = (INACTIVE_ALPHA * 255).toInt()
            }
    }

    private fun initResources() {
        val isDark: Boolean = SystemUtils.isDarkMode

        if (isDark != wasDark) {
            wasDark = isDark
        }

        colorActive = ContextCompat.getColor(mContext, android.R.color.system_accent1_400)
        colorActiveAlpha = changeAlpha(colorActive, ACTIVE_ALPHA)
    }

    private fun changeAlpha(color: Int, alpha: Float): Int {
        return changeAlpha(color, (alpha * 255).toInt())
    }

    private fun changeAlpha(color: Int, alpha: Int): Int {
        val alphaInRange = max(0.0, min(alpha.toDouble(), 255.0)).toInt()

        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        return Color.argb(alphaInRange, red, green, blue)
    }

    private fun createBrightnessDrawable(context: Context): LayerDrawable {
        val res = context.resources
        val cornerRadius = res.getDimensionPixelSize(
            res.getIdentifier(
                "rounded_slider_corner_radius",
                "dimen",
                context.packageName
            )
        )
        val height = res.getDimensionPixelSize(
            res.getIdentifier(
                "rounded_slider_height",
                "dimen",
                context.packageName
            )
        )
        val startPadding = context.toPx(15)
        val endPadding = context.toPx(15)

        // Create the background shape
        val radiusF = FloatArray(8)
        for (i in 0..7) {
            radiusF[i] = cornerRadius.toFloat()
        }

        val backgroundShape = ShapeDrawable(RoundRectShape(radiusF, null, null))
        backgroundShape.intrinsicHeight = height
        backgroundShape.alpha = (BRIGHTNESS_BAR_BACKGROUND_ALPHA * 255).toInt()
        backgroundShape.setTint(colorInactive)

        // Create the progress drawable
        var progressDrawable: RoundedCornerProgressDrawable? = null
        try {
            progressDrawable = RoundedCornerProgressDrawable(
                createBrightnessForegroundDrawable(context)
            )
            progressDrawable.alpha = (BRIGHTNESS_BAR_FOREGROUND_ALPHA * 255).toInt()
            progressDrawable.setTint(colorActive)
        } catch (ignored: Throwable) {
        }

        // Create the start and end drawables
        val startDrawable = ResourcesCompat.getDrawable(
            HookRes.modRes,
            R.drawable.ic_brightness_low,
            context.theme
        )
        val endDrawable = ResourcesCompat.getDrawable(
            HookRes.modRes,
            R.drawable.ic_brightness_full,
            context.theme
        )

        if (startDrawable != null && endDrawable != null) {
            startDrawable.setTint(colorActive)
            endDrawable.setTint(colorActive)
        }

        // Create the layer drawable
        val layers = arrayOf(backgroundShape, progressDrawable, startDrawable, endDrawable)
        val layerDrawable = LayerDrawable(layers)
        layerDrawable.setId(0, android.R.id.background)
        layerDrawable.setId(1, android.R.id.progress)
        layerDrawable.setLayerGravity(2, Gravity.START or Gravity.CENTER_VERTICAL)
        layerDrawable.setLayerGravity(3, Gravity.END or Gravity.CENTER_VERTICAL)
        layerDrawable.setLayerInsetStart(2, startPadding)
        layerDrawable.setLayerInsetEnd(3, endPadding)

        return layerDrawable
    }

    private fun createBrightnessForegroundDrawable(context: Context): LayerDrawable {
        val res = context.resources
        val rectangleDrawable = GradientDrawable()
        val cornerRadius = context.resources.getDimensionPixelSize(
            res.getIdentifier(
                "rounded_slider_corner_radius",
                "dimen",
                context.packageName
            )
        )

        rectangleDrawable.cornerRadius = cornerRadius.toFloat()
        rectangleDrawable.setColor(colorActive)

        val layerDrawable = LayerDrawable(arrayOf<Drawable>(rectangleDrawable))
        layerDrawable.setLayerGravity(0, Gravity.FILL_HORIZONTAL or Gravity.CENTER)

        val height = context.toPx(48)
        layerDrawable.setLayerSize(0, layerDrawable.getLayerWidth(0), height)

        return layerDrawable
    }

    companion object {
        private const val ACTIVE_ALPHA = 0.2f
        private const val INACTIVE_ALPHA = 0.4f
        private const val UNAVAILABLE_ALPHA = 0.3f
        private const val BRIGHTNESS_BAR_BACKGROUND_ALPHA = 0.3f
        private const val BRIGHTNESS_BAR_FOREGROUND_ALPHA = 0.2f
        private const val PM_LITE_BACKGROUND_CODE = 1
        private var fluidQsThemeEnabled = false
        private var fluidNotificationEnabled = false
        private var fluidPowerMenuEnabled = false
    }
}