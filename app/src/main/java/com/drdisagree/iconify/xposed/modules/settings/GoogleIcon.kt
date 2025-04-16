package com.drdisagree.iconify.xposed.modules.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.drdisagree.iconify.data.common.Const.SETTINGS_PACKAGE
import com.drdisagree.iconify.xposed.ModPack
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.XposedHook.Companion.findClass
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.callMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.hookMethod
import com.drdisagree.iconify.xposed.modules.extras.utils.toolkit.log
import com.drdisagree.iconify.xposed.utils.XPrefs.Xprefs
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Locale

@SuppressLint("DiscouragedApi")
class GoogleIcon(context: Context) : ModPack(context) {

    private var replaceZenModeIcon = false

    override fun updatePrefs(vararg key: String) {
        Xprefs.apply {
            replaceZenModeIcon = getBoolean("IconifyComponentSIP1.overlay", false)
        }
    }

    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        val homepagePreferenceClass = findClass("$SETTINGS_PACKAGE.widget.HomepagePreference")

        homepagePreferenceClass
            .hookMethod("onBindViewHolder")
            .runAfter { param ->
                if (!replaceZenModeIcon) return@runAfter

                val viewHolder = param.args[0]

                val mText = viewHolder.callMethod(
                    "findViewById",
                    android.R.id.title
                ) as TextView

                if (mText.text.toString().lowercase(Locale.getDefault()).contains("google")) {
                    val mIcon = viewHolder.callMethod(
                        "findViewById",
                        android.R.id.icon
                    ) as ImageView
                    val iconDrawable = getDrawableFromIconPack()

                    if (iconDrawable != null) {
                        mIcon.setImageDrawable(iconDrawable)
                    }
                }
            }
    }

    private fun getDrawableFromIconPack(): Drawable? {
        val packageName = "IconifyComponentSIP3.overlay"
        val drawableName = "googleg_icon_24"

        try {
            val pm = mContext.packageManager
            val resources: Resources = pm.getResourcesForApplication(packageName)

            val drawableId = resources.getIdentifier(drawableName, "drawable", packageName)

            if (drawableId != 0) {
                return ResourcesCompat.getDrawable(resources, drawableId, mContext.theme)
            } else {
                log(this@GoogleIcon, "Drawable not found: $drawableName")
            }
        } catch (e: PackageManager.NameNotFoundException) {
            log(this@GoogleIcon, "Package not found: $packageName\n$e")
        } catch (e: Resources.NotFoundException) {
            log(this@GoogleIcon, "Resource not found\n$e")
        }

        return null
    }
}