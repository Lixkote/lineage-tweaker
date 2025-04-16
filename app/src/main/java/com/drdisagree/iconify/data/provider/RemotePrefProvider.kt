package com.drdisagree.iconify.data.provider

import com.crossbowffs.remotepreferences.RemotePreferenceFile
import com.crossbowffs.remotepreferences.RemotePreferenceProvider
import com.drdisagree.iconify.BuildConfig
import com.drdisagree.iconify.data.common.Resources

class RemotePrefProvider : RemotePreferenceProvider(
    BuildConfig.APPLICATION_ID,
    arrayOf(RemotePreferenceFile(Resources.SHARED_XPREFERENCES, true))
)