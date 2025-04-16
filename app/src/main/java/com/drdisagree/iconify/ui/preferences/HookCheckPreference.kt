package com.drdisagree.iconify.ui.preferences

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.drdisagree.iconify.Iconify.Companion.appContextLocale
import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.common.Const.ACTION_HOOK_CHECK_REQUEST
import com.drdisagree.iconify.data.common.Const.ACTION_HOOK_CHECK_RESULT
import com.drdisagree.iconify.data.common.Preferences
import com.drdisagree.iconify.data.common.Preferences.XPOSED_HOOK_CHECK
import com.drdisagree.iconify.data.config.RPrefs
import com.drdisagree.iconify.xposed.utils.BootLoopProtector.PACKAGE_STRIKE_KEY_KEY
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.ref.WeakReference

class HookCheckPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    private val handler = Handler(Looper.getMainLooper())
    private val delayedHandler = Handler(Looper.getMainLooper())
    private var isHookSuccessful = false
    private var holderRef: WeakReference<PreferenceViewHolder>? = null
    private var hookPackages = context.resources.getStringArray(R.array.module_scope)
    private val intentFilterHookedSystemUI = IntentFilter().apply {
        addAction(ACTION_HOOK_CHECK_RESULT)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holderRef = WeakReference(holder)
        updateUI()
    }

    private fun updateUI() {
        holderRef?.get()?.itemView?.let { itemView ->
            if (hasBootlooped) {
                itemView.findViewById<TextView>(R.id.title)
                    .setText(R.string.xposed_module_bootlooped_title)
                itemView.findViewById<TextView>(R.id.summary)
                    .setText(R.string.xposed_module_bootlooped_desc)
            } else {
                itemView.findViewById<TextView>(R.id.title)
                    .setText(R.string.xposed_module_disabled_title)
                itemView.findViewById<TextView>(R.id.summary)
                    .setText(R.string.xposed_module_disabled_desc)
            }

            itemView.setOnClickListener {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_MAIN).apply {
                            setComponent(
                                ComponentName(
                                    "org.lsposed.manager",
                                    "org.lsposed.manager.ui.activity.MainActivity"
                                )
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (ignored: Exception) {
                }
            }

            itemView.findViewById<MaterialButton>(R.id.btn_more).setOnClickListener {
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.resources.getString(R.string.attention))
                    .setMessage(
                        if (!hasBootlooped) {
                            buildString {
                                append(
                                    (if (Preferences.isXposedOnlyMode) {
                                        appContextLocale.resources.getString(
                                            R.string.xposed_only_desc
                                        ) + "\n\n"
                                    } else {
                                        ""
                                    })
                                )
                                append(appContextLocale.resources.getString(R.string.lsposed_warn))
                            }
                        } else {
                            context.resources.getString(R.string.lsposed_bootloop_warn)
                        }
                    )
                    .setPositiveButton(context.resources.getString(R.string.understood)) { dialog: DialogInterface, _: Int ->
                        dialog.dismiss()
                    }
                    .setCancelable(true)
                    .show()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun initializeHookCheck() {
        delayedHandler.postDelayed(delayedHookCheck, 1000)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiverHookedSystemUI,
                intentFilterHookedSystemUI,
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                receiverHookedSystemUI,
                intentFilterHookedSystemUI
            )
        }

        handler.post(checkSystemUIHooked)
    }

    private val delayedHookCheck: Runnable = Runnable {
        if (!isHooked) {
            RPrefs.putBoolean(XPOSED_HOOK_CHECK, false)

            hasBootlooped = false
            for (packageName in hookPackages) {
                val strikeKey = "$PACKAGE_STRIKE_KEY_KEY$packageName"

                if (RPrefs.getInt(strikeKey, 0) >= 3) {
                    hasBootlooped = true
                    break
                }
            }

            notifyChanged()
        }
    }

    private val checkSystemUIHooked: Runnable = object : Runnable {
        override fun run() {
            checkXposedHooked()
//            handler.postDelayed(this, 1000) // repeat check every 1 second
        }
    }

    private val receiverHookedSystemUI: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_HOOK_CHECK_RESULT) {
                isHookSuccessful = true

                try {
                    delayedHandler.removeCallbacks(delayedHookCheck)
                } catch (ignored: Exception) {
                }

                isHooked = true

                RPrefs.putBoolean(XPOSED_HOOK_CHECK, true)
            }
        }
    }

    private fun checkXposedHooked() {
        isHookSuccessful = false

        object : CountDownTimer(1600, 800) {
            override fun onTick(millisUntilFinished: Long) {
                if (isHookSuccessful) {
                    cancel()
                }
            }

            override fun onFinish() {
                if (!isHookSuccessful) {
                    try {
                        delayedHandler.removeCallbacks(delayedHookCheck)
                    } catch (ignored: Exception) {
                    }

                    isHooked = false

                    RPrefs.putBoolean(XPOSED_HOOK_CHECK, false)
                }
            }
        }.start()

        Thread {
            try {
                context.sendBroadcast(Intent().setAction(ACTION_HOOK_CHECK_REQUEST))
            } catch (ignored: Exception) {
            }
        }.start()
    }

    override fun onDetached() {
        super.onDetached()
        try {
            handler.removeCallbacks(checkSystemUIHooked)
            delayedHandler.removeCallbacks(delayedHookCheck)
            context.unregisterReceiver(receiverHookedSystemUI)
        } catch (ignored: Exception) {
        }
    }

    companion object {
        private var hasBootlooped: Boolean = false
        var isHooked: Boolean = false
    }
}
