package com.drdisagree.iconify.ui.widgets

import android.content.Context
import android.util.AttributeSet
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import com.drdisagree.iconify.R
import com.google.android.material.materialswitch.MaterialSwitch

class MasterSwitchWidget : LinearLayout {

    private lateinit var container: LinearLayout
    private lateinit var titleTextView: TextView
    private lateinit var materialSwitch: MaterialSwitch
    private var beforeSwitchChangeListener: BeforeSwitchChangeListener? = null

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        inflate(context, R.layout.view_widget_master_switch, this)

        initializeId()

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.MasterSwitchWidget)

        setTitle(typedArray.getString(R.styleable.MasterSwitchWidget_titleText))
        isSwitchChecked = typedArray.getBoolean(R.styleable.MasterSwitchWidget_isChecked, false)

        typedArray.recycle()

        container.setOnClickListener {
            if (materialSwitch.isEnabled) {
                beforeSwitchChangeListener?.beforeSwitchChanged()
                materialSwitch.toggle()
            }
        }
    }

    fun setTitle(titleResId: Int) {
        titleTextView.setText(titleResId)
    }

    fun setTitle(title: String?) {
        titleTextView.text = title
    }

    var isSwitchChecked: Boolean
        get() = materialSwitch.isChecked
        set(isChecked) {
            materialSwitch.setChecked(isChecked)
        }

    fun setSwitchChangeListener(listener: CompoundButton.OnCheckedChangeListener?) {
        materialSwitch.setOnCheckedChangeListener(listener)
    }

    fun setBeforeSwitchChangeListener(listener: BeforeSwitchChangeListener?) {
        beforeSwitchChangeListener = listener
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        container.setEnabled(enabled)
        titleTextView.setEnabled(enabled)
        materialSwitch.setEnabled(enabled)
    }

    // to avoid listener bug, we need to re-generate unique id for each view
    private fun initializeId() {
        container = findViewById(R.id.container)
        titleTextView = findViewById(R.id.title)
        materialSwitch = findViewById(R.id.switch_widget)
        container.setId(generateViewId())
        titleTextView.setId(generateViewId())
        materialSwitch.setId(generateViewId())
    }

    fun interface BeforeSwitchChangeListener {
        fun beforeSwitchChanged()
    }
}
