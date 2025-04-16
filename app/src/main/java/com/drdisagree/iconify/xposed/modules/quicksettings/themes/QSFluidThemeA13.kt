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
import android.os.Build
import android.service.quicksettings.Tile
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
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
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setField
import com.drdisagree.iconify.xposed.modules.extras.views.RoundedCornerProgressDrawable
import com.drdisagree.iconify.xposed.utils.SystemUtils
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlin.math.max
import kotlin.math.min

@SuppressLint("DiscouragedApi")
class QSFluidThemeA13(context: Context) : ModPack(context) {

    private val colorActive = intArrayOf(
        mContext.resources.getColor(
            mContext.resources.getIdentifier(
                "android:color/system_accent1_400",
                "color",
                mContext.packageName
            ), mContext.theme
        )
    )
    private val colorActiveAlpha = intArrayOf(
        Color.argb(
            (ACTIVE_ALPHA * 255).toInt(), Color.red(
                colorActive[0]
            ), Color.green(colorActive[0]), Color.blue(colorActive[0])
        )
    )
    private var colorInactive = intArrayOf(
        SettingsLibUtils.getColorAttrDefaultColor(
            mContext,
            mContext.resources.getIdentifier("offStateColor", "attr", mContext.packageName)
        )
    )
    private val colorInactiveAlpha = intArrayOf(changeAlpha(colorInactive[0], INACTIVE_ALPHA))
    private var wasDark: Boolean = SystemUtils.isDarkMode
    private var mSlider: SeekBar? = null

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            fluidQsThemeEnabled = getBoolean(FLUID_QSPANEL, false)
            fluidNotifEnabled = fluidQsThemeEnabled &&
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
        val footerViewClass = findClass("$SYSTEMUI_PACKAGE.statusbar.notification.row.FooterView")
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

        // QS tile color
        qsTileViewImplClass
            .hookConstructor()
            .runAfter {
                colorInactive[0] = SettingsLibUtils.getColorAttrDefaultColor(
                    mContext,
                    mContext.resources.getIdentifier("offStateColor", "attr", mContext.packageName)
                )
                colorInactiveAlpha[0] = changeAlpha(colorInactive[0], INACTIVE_ALPHA)
            }

        qsTileViewImplClass
            .hookMethod("getBackgroundColorForState")
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                try {
                    if (param.args[0] as Int == Tile.STATE_ACTIVE) {
                        param.result = changeAlpha(colorActive[0], ACTIVE_ALPHA)
                    } else {
                        val inactiveColor = param.result as Int?

                        inactiveColor?.let {
                            colorInactive[0] = it
                            colorInactiveAlpha[0] = changeAlpha(it, INACTIVE_ALPHA)

                            if (param.args[0] as Int == Tile.STATE_INACTIVE) {
                                param.result = changeAlpha(it, INACTIVE_ALPHA)
                            } else if (param.args[0] as Int == Tile.STATE_UNAVAILABLE) {
                                param.result = changeAlpha(it, UNAVAILABLE_ALPHA)
                            }
                        }
                    }
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA13, throwable)
                }
            }

        // QS icon color
        qsIconViewImplClass
            .hookMethod("getIconColorForState", "getColor")
            .runBefore { param ->
                if (!fluidQsThemeEnabled) return@runBefore

                try {
                    if (param.args[1].getField("state") as Int == Tile.STATE_ACTIVE
                    ) {
                        param.result = colorActive[0]
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
                        (param.args[0] as ImageView).imageTintList =
                            ColorStateList.valueOf(colorActive[0])
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
                        param.thisObject.setField("mTint", colorActive[0])
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

                try {
                    val res = mContext.resources
                    val view = (param.thisObject as ViewGroup).findViewById<ViewGroup>(
                        res.getIdentifier(
                            "qs_footer_actions",
                            "id",
                            mContext.packageName
                        )
                    )

                    view.background.setTint(Color.TRANSPARENT)
                    view.elevation = 0f
                    setAlphaTintedDrawables(view, INACTIVE_ALPHA)

                    try {
                        val securityFooter = (view.findViewById<View>(
                            res.getIdentifier(
                                "security_footers_container",
                                "id",
                                mContext.packageName
                            )
                        ) as ViewGroup).getChildAt(0)

                        securityFooter.background.setTint(colorInactive[0])
                        securityFooter.background.alpha = (INACTIVE_ALPHA * 255).toInt()
                    } catch (ignored: Throwable) {
                    }

                    try {
                        val multiUserSwitch = view.findViewById<View>(
                            res.getIdentifier(
                                "multi_user_switch",
                                "id",
                                mContext.packageName
                            )
                        )

                        multiUserSwitch.background.setTint(colorInactive[0])
                        multiUserSwitch.background.alpha = (INACTIVE_ALPHA * 255).toInt()
                    } catch (ignored: Throwable) {
                    }
                    try {
                        val pmButtonContainer = view.findViewById<ViewGroup>(
                            res.getIdentifier(
                                "pm_lite",
                                "id",
                                mContext.packageName
                            )
                        )

                        pmButtonContainer.background.alpha = (ACTIVE_ALPHA * 255).toInt()
                        pmButtonContainer.background.setTint(colorActive[0])
                        (pmButtonContainer.getChildAt(0) as ImageView).setColorFilter(
                            colorActive[0], PorterDuff.Mode.SRC_IN
                        )
                    } catch (ignored: Throwable) {
                        val pmButton = view.findViewById<ImageView>(
                            res.getIdentifier(
                                "pm_lite",
                                "id",
                                mContext.packageName
                            )
                        )

                        pmButton.background.alpha = (ACTIVE_ALPHA * 255).toInt()
                        pmButton.background.setTint(colorActive[0])
                        pmButton.imageTintList = ColorStateList.valueOf(colorActive[0])
                    }
                } catch (ignored: Throwable) {
                }
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
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA13, throwable)
                }
            }

        brightnessControllerClass
            .hookMethod("updateIcon")
            .suppressError()
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                try {
                    (param.thisObject.getField("mIcon") as ImageView).imageTintList =
                        ColorStateList.valueOf(colorActive[0])

                    (param.thisObject.getField("mIcon") as ImageView).backgroundTintList =
                        ColorStateList.valueOf(colorActiveAlpha[0])
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA13, throwable)
                }
            }

        brightnessSliderControllerClass
            .hookConstructor()
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                try {
                    (param.thisObject.getField("mIcon") as ImageView).imageTintList =
                        ColorStateList.valueOf(colorActive[0])

                    (param.thisObject.getField(
                        "mIcon"
                    ) as ImageView).backgroundTintList =
                        ColorStateList.valueOf(colorActiveAlpha[0])
                } catch (throwable: Throwable) {
                    try {
                        (param.thisObject.getField(
                            "mIconView"
                        ) as ImageView).imageTintList =
                            ColorStateList.valueOf(colorActive[0])

                        (param.thisObject.getField(
                            "mIconView"
                        ) as ImageView).backgroundTintList =
                            ColorStateList.valueOf(colorActiveAlpha[0])
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
                    (param.thisObject.getField("mIcon") as ImageView).imageTintList =
                        ColorStateList.valueOf(colorActive[0])

                    (param.thisObject.getField("mIcon") as ImageView).backgroundTintList =
                        ColorStateList.valueOf(colorActiveAlpha[0])
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA13, throwable)
                }
            }

        brightnessMirrorControllerClass
            .hookMethod(
                "onFinishInflate",
                "reinflate"
            )
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                try {
                    val mBrightnessMirror = param.thisObject.getField(
                        "mBrightnessMirror"
                    ) as FrameLayout

                    mBrightnessMirror.background.alpha = (INACTIVE_ALPHA * 255).toInt()
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA13, throwable)
                }
            }

        qsPanelClass
            .hookMethod("updateResources")
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                try {
                    (param.thisObject.getField("mAutoBrightnessView") as View)
                        .background.setTint(colorActiveAlpha[0])
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
                        param.result = colorActive[0]
                    }
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA13, throwable)
                }
            }

        // QS tile secondary label color
        qsTileViewImplClass
            .hookMethod("getSecondaryLabelColorForState")
            .runBefore { param ->
                if (!fluidQsThemeEnabled) return@runBefore

                try {
                    if (param.args[0] as Int == Tile.STATE_ACTIVE) {
                        param.result = colorActive[0]
                    }
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA13, throwable)
                }
            }

        qsTileViewImplClass
            .hookConstructor()
            .runAfter { param ->
                if (!fluidQsThemeEnabled) return@runAfter

                colorInactive[0] = changeAlpha(
                    param.thisObject.getField(
                        "colorInactive"
                    ) as Int, 1.0f
                )

                initResources()

                // For LineageOS based roms
                try {
                    param.thisObject.setField(
                        "colorActive",
                        changeAlpha(colorActive[0], ACTIVE_ALPHA)
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
                        colorActive[0]
                    )

                    param.thisObject.setField(
                        "colorSecondaryLabelActive",
                        colorActive[0]
                    )
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA13, throwable)
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
                    log(this@QSFluidThemeA13, throwable)
                }
            }

        qsTileViewImplClass
            .hookMethod("updateResources")
            .suppressError()
            .runBefore { param ->
                if (!fluidQsThemeEnabled) return@runBefore

                colorInactive[0] = changeAlpha(
                    param.thisObject.getField(
                        "colorInactive"
                    ) as Int, 1.0f
                )

                initResources()

                try {
                    param.thisObject.setField(
                        "colorActive",
                        changeAlpha(colorActive[0], ACTIVE_ALPHA)
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
                        colorActive[0]
                    )

                    param.thisObject.setField(
                        "colorSecondaryLabelActive",
                        colorActive[0]
                    )
                } catch (throwable: Throwable) {
                    log(this@QSFluidThemeA13, throwable)
                }
            }

        // Notifications
        activatableNotificationViewClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                if (!fluidQsThemeEnabled || !fluidNotifEnabled) return@runAfter

                val mBackgroundNormal =
                    param.thisObject.getField("mBackgroundNormal") as View?
                mBackgroundNormal?.alpha = INACTIVE_ALPHA
            }

        // Notification expand/collapse pill
        notificationExpandButtonClass
            .hookMethod("onFinishInflate")
            .runAfter { param ->
                if (!fluidQsThemeEnabled || !fluidNotifEnabled) return@runAfter

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
                if (!fluidQsThemeEnabled || !fluidNotifEnabled) return

                try {
                    val mManageButton =
                        param.thisObject.getField("mManageButton") as Button
                    val mClearAllButton = try {
                        param.thisObject.getField("mClearAllButton")
                    } catch (ignored: Throwable) {
                        param.thisObject.getField("mDismissButton")
                    } as Button

                    mManageButton.background?.alpha = (INACTIVE_ALPHA * 255).toInt()
                    mClearAllButton.background?.alpha = (INACTIVE_ALPHA * 255).toInt()
                } catch (ignored: Throwable) {
                }
            }
        }

        footerViewClass
            .hookMethod("onFinishInflate")
            .run(updateNotificationFooterButtons)

        footerViewClass
            .hookMethod("updateColors")
            .run(updateNotificationFooterButtons)

        // Power menu
        val globalActionsDialogLiteSinglePressActionClass =
            findClass("$SYSTEMUI_PACKAGE.globalactions.GlobalActionsDialogLite\$SinglePressAction")
        val globalActionsLayoutLiteClass =
            findClass("$SYSTEMUI_PACKAGE.globalactions.GlobalActionsLayoutLite")

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

        // Footer button A12
        if (Build.VERSION.SDK_INT < 33) {
            val footerActionsViewClass = findClass("$SYSTEMUI_PACKAGE.qs.FooterActionsView")

            val updateFooterButtons: XC_MethodHook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val parent = param.thisObject as ViewGroup
                    val childCount = parent.childCount

                    for (i in 0 until childCount) {
                        val childView = parent.getChildAt(i)
                        childView.background?.setTint(colorInactive[0])
                        childView.background?.alpha = (INACTIVE_ALPHA * 255).toInt()
                    }
                }
            }

            footerActionsViewClass
                .hookMethod("onFinishInflate")
                .run(updateFooterButtons)

            footerActionsViewClass
                .hookMethod("updateResources")
                .run(updateFooterButtons)
        }
    }

    private fun initResources() {
        val isDark: Boolean = SystemUtils.isDarkMode

        if (isDark != wasDark) {
            wasDark = isDark
        }

        colorActive[0] = mContext.resources.getColor(
            mContext.resources.getIdentifier(
                "android:color/system_accent1_400",
                "color",
                mContext.packageName
            ), mContext.theme
        )

        colorActiveAlpha[0] = Color.argb(
            (ACTIVE_ALPHA * 255).toInt(), Color.red(
                colorActive[0]
            ), Color.green(colorActive[0]), Color.blue(colorActive[0])
        )

        colorInactiveAlpha[0] = changeAlpha(colorInactive[0], INACTIVE_ALPHA)
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
        val cornerRadius = context.resources.getDimensionPixelSize(
            res.getIdentifier(
                "rounded_slider_corner_radius",
                "dimen",
                context.packageName
            )
        )
        val height = context.resources.getDimensionPixelSize(
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
        backgroundShape.paint.color = changeAlpha(colorInactiveAlpha[0], UNAVAILABLE_ALPHA)

        // Create the progress drawable
        var progressDrawable: RoundedCornerProgressDrawable? = null
        try {
            progressDrawable =
                RoundedCornerProgressDrawable(createBrightnessForegroundDrawable(context))
            progressDrawable.alpha = (ACTIVE_ALPHA * 255).toInt()
            progressDrawable.setTint(colorActive[0])
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
            startDrawable.setTint(colorActive[0])
            endDrawable.setTint(colorActive[0])
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
        rectangleDrawable.setColor(colorActive[0])

        val layerDrawable = LayerDrawable(arrayOf<Drawable>(rectangleDrawable))
        layerDrawable.setLayerGravity(0, Gravity.FILL_HORIZONTAL or Gravity.CENTER)

        val height = context.toPx(48)
        layerDrawable.setLayerSize(0, layerDrawable.getLayerWidth(0), height)

        return layerDrawable
    }

    @Suppress("SameParameterValue")
    private fun setAlphaTintedDrawables(view: View, alpha: Float) {
        setAlphaTintedDrawables(view, (alpha * 255).toInt())
    }

    private fun setAlphaTintedDrawables(view: View, alpha: Int) {
        if (view is ViewGroup) {
            val childCount: Int = view.childCount

            for (i in 0 until childCount) {
                val child: View = view.getChildAt(i)
                setAlphaTintedDrawablesRecursively(child, alpha)
            }
        }
    }

    private fun setAlphaTintedDrawablesRecursively(view: View, alpha: Int) {
        val backgroundDrawable = view.background

        if (backgroundDrawable != null) {
            backgroundDrawable.setTint(colorInactive[0])
            backgroundDrawable.alpha = alpha
        }

        if (view is ViewGroup) {
            val childCount: Int = view.childCount

            for (i in 0 until childCount) {
                val child: View = view.getChildAt(i)
                setAlphaTintedDrawablesRecursively(child, alpha)
            }
        }
    }

    companion object {
        private const val ACTIVE_ALPHA = 0.2f
        private const val INACTIVE_ALPHA = ACTIVE_ALPHA + 0.2f
        private const val UNAVAILABLE_ALPHA = INACTIVE_ALPHA - 0.1f
        private var fluidQsThemeEnabled = false
        private var fluidNotifEnabled = false
        private var fluidPowerMenuEnabled = false
    }
}