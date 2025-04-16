package com.drdisagree.iconify.services

import android.content.Context
import android.graphics.Bitmap
import com.drdisagree.iconify.IExtractSubjectCallback

class RootProviderProxy : BaseRootProviderProxy() {

    override fun createProxy(): BaseRootProviderProxyIPC {
        return RootProviderProxyIPC(this)
    }

    inner class RootProviderProxyIPC(context: Context) : BaseRootProviderProxyIPC(context) {

        override fun extractWallpaperSubject(
            input: Bitmap,
            callback: IExtractSubjectCallback,
            resultPath: String
        ) {
            // do nothing
        }
    }
}