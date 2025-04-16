package com.drdisagree.iconify.ui.preferences

import android.content.Context
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * Copied from setup wizard. This TextView performed two functions. The first is to make it so the
 * link behaves properly and becomes clickable. The second was that it made the link visible to
 * accessibility services, but from O forward support for links is provided natively.
 */
class LinkTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatTextView(context, attrs) {
    override fun setText(text: CharSequence, type: BufferType) {
        super.setText(text, type)
        if (text is Spanned) {
            val spans =
                text.getSpans(0, text.length, ClickableSpan::class.java)
            if (spans.size > 0) {
                movementMethod = LinkMovementMethod.getInstance()
            }
        }
    }
}
