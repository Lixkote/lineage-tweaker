package com.drdisagree.iconify.xposed.modules.quicksettings.themes

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.common.Preferences.DUALTONE_QSPANEL
import com.drdisagree.iconify.data.common.Preferences.LIGHT_QSPANEL
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.getField
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookConstructor
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.setField
import com.drdisagree.iconify.xposed.utils.SystemUtils
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

@SuppressLint("DiscouragedApi")
class QSLightThemeA12(context: Context) : ModPack(context) {

    private var mBehindColors: Any? = null
    private var isDark: Boolean
    private val qsLightThemeOverlay = "IconifyComponentQSLT.overlay"
    private val qsDualToneOverlay = "IconifyComponentQSDT.overlay"

    init {
        isDark = SystemUtils.isDarkMode
    }

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            lightQSHeaderEnabled = getBoolean(LIGHT_QSPANEL, false)
            dualToneQSEnabled = lightQSHeaderEnabled && getBoolean(DUALTONE_QSPANEL, false)
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
        val utilsClass = findClass("com.android.settingslib.Utils")
        val ongoingPrivacyChipClass = findClass("$SYSTEMUI_PACKAGE.privacy.OngoingPrivacyChip")
        val fragmentHostManagerClass = findClass("$SYSTEMUI_PACKAGE.fragments.FragmentHostManager")
        val scrimControllerClass = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimController")
        val gradientColorsClass =
            findClass("com.android.internal.colorextraction.ColorExtractor.GradientColors")!!
        val statusbarClass = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.StatusBar")
        val interestingConfigChangesClass =
            findClass("com.android.settingslib.applications.InterestingConfigChanges")!!
        val scrimStateEnum = findClass("$SYSTEMUI_PACKAGE.statusbar.phone.ScrimState")!!

        try {
            mBehindColors = gradientColorsClass.getDeclaredConstructor().newInstance()
        } catch (throwable: Throwable) {
            log(this@QSLightThemeA12, throwable)
        }

        scrimControllerClass
            .hookMethod("onUiModeChanged")
            .runAfter {
                try {
                    mBehindColors = gradientColorsClass.getDeclaredConstructor().newInstance()
                } catch (throwable: Throwable) {
                    log(this@QSLightThemeA12, throwable)
                }
            }

        scrimControllerClass
            .hookMethod("updateScrims")
            .runAfter { param ->
                if (!dualToneQSEnabled) return@runAfter

                try {
                    val mScrimBehind = param.thisObject.getField("mScrimBehind")
                    val mBlankScreen = param.thisObject.getField("mBlankScreen") as Boolean
                    val alpha = mScrimBehind.callMethod("getViewAlpha") as Float
                    val animateBehindScrim = alpha != 0f && !mBlankScreen

                    mScrimBehind.callMethod(
                        "setColors",
                        mBehindColors,
                        animateBehindScrim
                    )
                } catch (throwable: Throwable) {
                    log(this@QSLightThemeA12, throwable)
                }
            }

        scrimControllerClass
            .hookMethod("updateThemeColors")
            .runAfter {
                if (!dualToneQSEnabled) return@runAfter

                try {
                    @SuppressLint("DiscouragedApi") val states = callStaticMethod(
                        utilsClass,
                        "getColorAttr",
                        mContext,
                        mContext.resources.getIdentifier(
                            "android:attr/colorSurfaceHeader",
                            "attr",
                            mContext.packageName
                        )
                    ) as ColorStateList

                    val surfaceBackground = states.defaultColor
                    val accentStates = callStaticMethod(
                        utilsClass,
                        "getColorAccent",
                        mContext
                    ) as ColorStateList
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
                    log(this@QSLightThemeA12, throwable)
                }
            }

        ongoingPrivacyChipClass
            .hookMethod("updateResources")
            .runAfter { param ->
                if (!lightQSHeaderEnabled) return@runAfter

                try {
                    val iconColor = mContext.resources.getColor(
                        mContext.resources.getIdentifier(
                            "android:color/system_neutral1_900",
                            "color",
                            mContext.packageName
                        ), mContext.theme
                    )

                    param.thisObject.setField("iconColor", iconColor)
                } catch (throwable: Throwable) {
                    log(this@QSLightThemeA12, throwable)
                }
            }

        scrimControllerClass
            .hookMethod("applyStateToAlpha", "applyState")
            .runAfter { param ->
                if (!lightQSHeaderEnabled) return@runAfter

                try {
                    val mClipsQsScrim =
                        param.thisObject.getField("mClipsQsScrim") as Boolean

                    if (mClipsQsScrim) {
                        param.thisObject.setField("mBehindTint", Color.TRANSPARENT)
                    }
                } catch (throwable: Throwable) {
                    log(this@QSLightThemeA12, throwable)
                }
            }

        val constants: Array<out Any> = scrimStateEnum.enumConstants ?: arrayOf()
        constants.forEach { constant ->
            when (constant.toString()) {
                "KEYGUARD" -> constant.javaClass
                    .hookMethod("prepare")
                    .parameters(scrimStateEnum)
                    .runAfter { param ->
                        if (!lightQSHeaderEnabled) return@runAfter

                        val mClipQsScrim = param.thisObject.getField(
                            "mClipQsScrim"
                        ) as Boolean

                        if (mClipQsScrim) {
                            param.thisObject.callMethod(
                                "updateScrimColor",
                                param.thisObject.getField("mScrimBehind"),
                                1f,
                                Color.TRANSPARENT
                            )
                        }
                    }

                "BOUNCER" -> constant.javaClass
                    .hookMethod("prepare")
                    .parameters(scrimStateEnum)
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
                                param.thisObject.callMethod(
                                    "updateScrimColor",
                                    param.thisObject.getField("mScrimBehind"),
                                    1f,
                                    Color.TRANSPARENT
                                )
                            }
                        }

                    constant.javaClass
                        .hookMethod("getBehindTint")
                        .suppressError()
                        .runAfter { param ->
                            if (!lightQSHeaderEnabled) return@runAfter

                            param.result = Color.TRANSPARENT
                        }
                }

                "UNLOCKED" -> constant.javaClass
                    .hookMethod("prepare")
                    .parameters(scrimStateEnum)
                    .runAfter { param ->
                        if (!lightQSHeaderEnabled) return@runAfter

                        param.thisObject.setField(
                            "mBehindTint",
                            Color.TRANSPARENT
                        )

                        param.thisObject.callMethod(
                            "updateScrimColor",
                            param.thisObject.getField("mScrimBehind"),
                            1f,
                            Color.TRANSPARENT
                        )
                    }
            }
        }

        fragmentHostManagerClass
            .hookConstructor()
            .runBefore { param ->
                if (!lightQSHeaderEnabled) return@runBefore

                try {
                    param.thisObject.setField(
                        "mConfigChanges",
                        interestingConfigChangesClass.getDeclaredConstructor(
                            Int::class.javaPrimitiveType
                        ).newInstance(0x40000000 or 0x0004 or 0x0100 or -0x80000000 or 0x0200)
                    )
                } catch (throwable: Throwable) {
                    log(this@QSLightThemeA12, throwable)
                }
            }

        statusbarClass
            .hookConstructor()
            .runAfter { param ->
                try {
                    param.thisObject.getField("mOnColorsChangedListener").javaClass
                        .hookMethod("onColorsChanged")
                        .runAfter { applyOverlays(true) }
                } catch (ignored: Throwable) {
                }
            }
    }

    @Suppress("SameParameterValue")
    private fun applyOverlays(force: Boolean) {
        val isCurrentlyDark: Boolean = SystemUtils.isDarkMode
        if (isCurrentlyDark == isDark && !force) return

        isDark = isCurrentlyDark

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

    companion object {
        private var lightQSHeaderEnabled = false
        private var dualToneQSEnabled = false
    }
}