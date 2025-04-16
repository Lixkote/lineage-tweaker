package com.drdisagree.iconify

import android.annotation.SuppressLint
import android.os.Build
import com.drdisagree.iconify.Iconify.Companion.appContext
import com.drdisagree.iconify.xposed.modules.extras.utils.BitmapSubjectSegmenter

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseSplashActivity() {

    override fun initializeMLKit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            BitmapSubjectSegmenter(appContext)
        }
    }
}
