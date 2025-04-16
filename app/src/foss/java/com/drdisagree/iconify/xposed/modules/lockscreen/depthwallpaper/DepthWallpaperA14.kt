package com.drdisagree.iconify.xposed.modules.lockscreen.depthwallpaper

import android.content.Context
import android.graphics.Bitmap

class DepthWallpaperA14(context: Context) : BaseDepthWallpaperA14(context) {

    override fun handleSubjectExtraction(scaledWallpaper: Bitmap?) {
        if (mAiMode != 0) {
            sendPluginIntent()
        }
    }
}