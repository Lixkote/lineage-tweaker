package com.drdisagree.iconify.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.drdisagree.iconify.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Locale

object MiscUtils {

    @JvmStatic
    fun showAlertDialog(
        context: Context,
        restartSystemUI: Boolean = false,
        restartDevice: Boolean = false,
        switchTheme: Boolean = false,
        rotateDevice: Boolean = false
    ) {
        val title = when {
            restartSystemUI -> R.string.systemui_restart_required_title
            restartDevice -> R.string.device_restart_required_title
            switchTheme -> R.string.switch_theme_required_title
            rotateDevice -> R.string.device_rotation_required_title
            else -> R.string.systemui_restart_required_title
        }
        val message = when {
            restartSystemUI -> R.string.systemui_restart_required_desc
            restartDevice -> R.string.device_restart_required_desc
            switchTheme -> R.string.switch_theme_required_desc
            rotateDevice -> R.string.device_rotation_required_desc
            else -> R.string.systemui_restart_required_desc
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(title))
            .setMessage(context.getString(message))
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    fun requiresNewToastStyle(): Boolean {
        val isAndroid15 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
        val patchLevel = Build.VERSION.SECURITY_PATCH
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        return try {
            val patchDate = sdf.parse(patchLevel)
            val thresholdDate = sdf.parse("2024-12-01")

            isAndroid15 && patchDate != null && patchDate.after(thresholdDate)
        } catch (e: Exception) {
            Log.e("SECURITY_PATCH_CHECK", "Error parsing security patch date", e)
            false
        }
    }
}