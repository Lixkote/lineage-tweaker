package com.drdisagree.iconify.xposed.modules.volume.styles

import android.graphics.drawable.Drawable

abstract class VolumeStyleBase {
    abstract fun createVolumeDrawerSelectionBgDrawable(): Drawable
    abstract fun createVolumeRowSeekbarDrawable(): Drawable
    abstract fun createVolumeRowSeekbarProgressDrawable(): Drawable
}