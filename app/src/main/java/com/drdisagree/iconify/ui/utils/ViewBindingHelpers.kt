package com.drdisagree.iconify.ui.utils

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

object ViewBindingHelpers {

    fun ImageView.setImageUrl(url: String) {
        Glide.with(context).load(url.replace("http://", "https://"))
            .apply(RequestOptions.centerCropTransform())
            .transition(DrawableTransitionOptions.withCrossFade()).into(this)
    }

    fun ImageView.setRoundImageUrl(url: String) {
        Glide.with(context).load(url.replace("http://", "https://"))
            .apply(RequestOptions.centerCropTransform())
            .circleCrop()
            .transition(DrawableTransitionOptions.withCrossFade()).into(this)
    }

    fun ImageView.setDrawable(drawable: Drawable?) {
        Glide.with(context).load(drawable).into(this)
    }

    fun ImageView.setDrawableWithAnimation(drawable: Drawable?) {
        Glide.with(context).load(drawable)
            .transition(DrawableTransitionOptions.withCrossFade()).into(this)
    }

    fun ViewGroup.setDrawable(drawable: Drawable?) {
        Glide.with(context).load(drawable).into(object : CustomTarget<Drawable?>() {
            override fun onResourceReady(
                resource: Drawable,
                transition: Transition<in Drawable?>?
            ) {
                background = resource
            }

            override fun onLoadCleared(placeholder: Drawable?) {}
        })
    }

    fun ViewGroup.setDrawableWithAnimation(drawable: Drawable?) {
        Glide.with(context).load(drawable)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(object : CustomTarget<Drawable?>() {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable?>?
                ) {
                    background = resource
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    fun ImageView.setBitmap(bitmap: Bitmap?) {
        val drawable: Drawable = BitmapDrawable(context.resources, bitmap)
        setDrawable(drawable)
    }

    fun ImageView.setBitmapWithAnimation(bitmap: Bitmap?) {
        val drawable: Drawable = BitmapDrawable(context.resources, bitmap)
        setDrawableWithAnimation(drawable)
    }
}