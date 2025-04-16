package com.drdisagree.iconify.xposed.modules.lockscreen.depthwallpaper

import android.content.Context
import android.graphics.Bitmap

class DepthWallpaperA15(context: Context) : BaseDepthWallpaperA15(context) {

    override fun handleSubjectExtraction(scaledWallpaper: Bitmap?) {
        if (mAiMode != 0) {
            sendPluginIntent()
        }
    }
}