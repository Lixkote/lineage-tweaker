package com.drdisagree.iconify.data.models

import android.content.Intent
import android.net.Uri
import android.view.View
import com.drdisagree.iconify.Iconify.Companion.appContext

class InfoModel {

    var icon: Any = 0
    var layout = 0
    var title: String? = null
    var desc: String? = null
    var onClickListener: View.OnClickListener? = null

    constructor(title: String?) {
        this.title = title
    }

    constructor(layout: Int) {
        this.layout = layout
    }

    constructor(title: String?, desc: String?, url: String?, icon: Any) {
        this.title = title
        this.desc = desc
        this.icon = icon
        this.onClickListener = View.OnClickListener {
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(url)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    constructor(title: String?, desc: String?, onClickListener: View.OnClickListener?, icon: Any) {
        this.title = title
        this.desc = desc
        this.icon = icon
        this.onClickListener = onClickListener
    }
}
