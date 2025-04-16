package com.drdisagree.iconify.ui.widgets

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.drdisagree.iconify.R
import com.drdisagree.iconify.utils.HapticUtils.weakVibrate
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.text.DecimalFormat
import java.util.Objects
import kotlin.math.abs

class SliderWidget : RelativeLayout {

    private lateinit var container: LinearLayout
    private lateinit var titleTextView: TextView
    private lateinit var summaryTextView: TextView
    private lateinit var materialSlider: Slider
    private lateinit var minusButton: MaterialButton
    private lateinit var plusButton: MaterialButton
    private lateinit var resetButton: MaterialButton
    private var valueFormat: String? = ""
    private var defaultValue = 0
    private var valueFrom = 0
    private var valueTo = 100
    private var tickInterval = 1
    private var outputScale = 1f
    private var isDecimalFormat = false
    private var decimalFormat: String? = "#.#"
    private var resetClickListener: OnLongClickListener? = null
    private var onSliderTouchListener: Slider.OnSliderTouchListener? = null
    private var tickVisible: Boolean = false
    private var showResetButton: Boolean = false
    private var showController: Boolean = false

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
        inflate(context, R.layout.view_widget_slider, this)

        initializeId()

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SliderWidget)

        valueFormat = typedArray.getString(R.styleable.SliderWidget_valueFormat)
        defaultValue = typedArray.getInt(R.styleable.SliderWidget_sliderDefaultValue, Int.MAX_VALUE)
        valueFrom = typedArray.getInt(R.styleable.SliderWidget_sliderValueFrom, 0)
        valueTo = typedArray.getInt(R.styleable.SliderWidget_sliderValueTo, 100)
        sliderValue = typedArray.getInt(
            R.styleable.SliderWidget_sliderValue,
            typedArray.getInt(R.styleable.SliderWidget_sliderDefaultValue, 50)
        )
        tickInterval = typedArray.getInt(R.styleable.SliderWidget_sliderStepSize, 1)
        isDecimalFormat = typedArray.getBoolean(R.styleable.SliderWidget_isDecimalFormat, false)
        decimalFormat = typedArray.getString(R.styleable.SliderWidget_decimalFormat)
        outputScale = typedArray.getFloat(R.styleable.SliderWidget_outputScale, 1f)
        showResetButton = typedArray.getBoolean(R.styleable.SliderWidget_showResetButton, false)
        showController = typedArray.getBoolean(R.styleable.SliderWidget_showController, false)
        tickVisible = typedArray.getBoolean(
            R.styleable.SliderWidget_tickVisible,
            abs(valueTo - valueFrom) <= 25
        )

        setTitle(typedArray.getString(R.styleable.SliderWidget_titleText))
        setSliderValueFrom(valueFrom)
        setSliderValueTo(valueTo)
        setSliderStepSize(tickInterval)
        materialSlider.isTickVisible = tickVisible

        typedArray.recycle()

        if (valueFormat == null) {
            valueFormat = ""
        }

        if (decimalFormat == null) {
            decimalFormat = "#.#"
        }

        setSelectedText()
        handleResetButtonState()
        setOnSliderTouchListener(null)
        setResetClickListener(null)

        if (showController) {
            minusButton.visibility = View.VISIBLE
            plusButton.visibility = View.VISIBLE

            minusButton.setOnClickListener { v: View ->
                v.weakVibrate()
                if (sliderValue <= valueFrom) return@setOnClickListener

                sliderValue = (sliderValue - tickInterval).coerceAtLeast(valueFrom)
            }

            plusButton.setOnClickListener { v: View ->
                v.weakVibrate()
                if (sliderValue >= valueTo) return@setOnClickListener

                sliderValue = (sliderValue + tickInterval).coerceAtMost(valueTo)
            }

            updateControllerButtons()
        } else {
            minusButton.visibility = View.GONE
            plusButton.visibility = View.GONE
        }
    }

    fun setTitle(titleResId: Int) {
        titleTextView.setText(titleResId)
    }

    fun setTitle(title: String?) {
        titleTextView.text = title
    }

    fun setSelectedText() {
        summaryTextView.text =
            if (valueFormat!!.isBlank() || valueFormat!!.isEmpty()) context.getString(
                R.string.opt_selected1, (
                        if (!isDecimalFormat) (materialSlider.value / outputScale).toInt() else DecimalFormat(
                            decimalFormat
                        ).format((materialSlider.value / outputScale).toDouble())
                        ).toString()
            ) else context.getString(
                R.string.opt_selected2,
                if (!isDecimalFormat) materialSlider.value.toInt().toString() else DecimalFormat(
                    decimalFormat
                ).format((materialSlider.value / outputScale).toDouble()),
                valueFormat
            )
    }

    fun setSliderStepSize(value: Int) {
        materialSlider.stepSize = value.toFloat()
    }

    var sliderValue: Int
        get() = materialSlider.value.toInt()
        set(value) {
            materialSlider.value = value.toFloat()
            setSelectedText()
            handleResetButtonState()
            if (showController) updateControllerButtons()
            notifyOnSliderTouchStopped(materialSlider)
        }

    fun setSliderValueFrom(value: Int) {
        materialSlider.valueFrom = value.toFloat()
    }

    fun setSliderValueTo(value: Int) {
        materialSlider.valueTo = value.toFloat()
    }

    fun setIsDecimalFormat(isDecimalFormat: Boolean) {
        this.isDecimalFormat = isDecimalFormat
        setSelectedText()
    }

    fun setDecimalFormat(decimalFormat: String) {
        this.decimalFormat = Objects.requireNonNullElse(decimalFormat, "#.#")
        setSelectedText()
    }

    fun setOutputScale(scale: Float) {
        outputScale = scale
        setSelectedText()
    }

    fun setOnSliderTouchListener(listener: Slider.OnSliderTouchListener?) {
        onSliderTouchListener = listener

        setOnSliderTouchListenerOnce()
    }

    fun setOnSliderChangeListener(listener: Slider.OnChangeListener) {
        materialSlider.addOnChangeListener(listener)
    }

    fun setResetClickListener(listener: OnLongClickListener?) {
        resetClickListener = listener

        setResetClickListenerOnce()
    }

    fun resetSlider() {
        resetButton.performLongClick()
    }

    private fun notifyOnSliderTouchStarted(slider: Slider) {
        onSliderTouchListener?.onStartTrackingTouch(slider)
    }

    private fun notifyOnSliderTouchStopped(slider: Slider) {
        onSliderTouchListener?.onStopTrackingTouch(slider)
    }

    private fun notifyOnResetClicked(v: View) {
        v.weakVibrate()
        resetClickListener?.onLongClick(v)
    }

    private fun handleResetButtonState() {
        if (defaultValue != Int.MAX_VALUE) {
            resetButton.visibility = VISIBLE
            resetButton.isEnabled = isEnabled && materialSlider.value != defaultValue.toFloat()
        } else {
            resetButton.visibility = GONE
        }
    }

    private fun updateControllerButtons() {
        val currentValue = materialSlider.value
        minusButton.isEnabled = currentValue > valueFrom
        plusButton.isEnabled = currentValue < valueTo
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        container.isEnabled = enabled
        titleTextView.isEnabled = enabled
        summaryTextView.isEnabled = enabled
        resetButton.isEnabled = enabled
        materialSlider.isEnabled = enabled
    }

    // to avoid listener bug, we need to re-generate unique id for each view
    private fun initializeId() {
        container = findViewById(R.id.container)
        titleTextView = findViewById(R.id.title)
        summaryTextView = findViewById(R.id.summary)
        materialSlider = findViewById(R.id.slider_widget)
        minusButton = findViewById(R.id.minus_button)
        plusButton = findViewById(R.id.plus_button)
        resetButton = findViewById(R.id.reset_button)
        container.id = generateViewId()
        titleTextView.id = generateViewId()
        summaryTextView.id = generateViewId()
        materialSlider.id = generateViewId()
        minusButton.id = generateViewId()
        plusButton.id = generateViewId()
        resetButton.id = generateViewId()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return SavedState(superState, materialSlider.value)
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        materialSlider.value = state.sliderValue
        setSelectedText()
        handleResetButtonState()
        if (showController) updateControllerButtons()
    }

    @Parcelize
    class SavedState(
        private val parentState: @RawValue Parcelable?,
        val sliderValue: Float
    ) : BaseSavedState(parentState)

    companion object {

        private fun SliderWidget.setOnSliderTouchListenerOnce() {
            if (getTag(R.id.tag_slider_touch_listener_set) != null) return

            materialSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    notifyOnSliderTouchStarted(slider)
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    setSelectedText()
                    handleResetButtonState()
                    notifyOnSliderTouchStopped(slider)
                    if (showController) updateControllerButtons()
                }
            })

            setTag(R.id.tag_slider_touch_listener_set, "set")

            materialSlider.setLabelFormatter {
                if (valueFormat!!.isBlank() || valueFormat!!.isEmpty()) (
                        if (!isDecimalFormat) (materialSlider.value / outputScale).toInt()
                        else DecimalFormat(decimalFormat)
                            .format((materialSlider.value / outputScale).toDouble())).toString() + valueFormat else (if (!isDecimalFormat) materialSlider.value.toInt()
                    .toString() else DecimalFormat(decimalFormat)
                    .format((materialSlider.value / outputScale).toDouble())) + valueFormat
            }
        }

        private fun SliderWidget.setResetClickListenerOnce() {
            if (getTag(R.id.tag_slider_reset_listener_set) != null) return

            resetButton.setOnClickListener { v: View ->
                if (defaultValue == Int.MAX_VALUE) {
                    return@setOnClickListener
                }

                materialSlider.value = defaultValue.toFloat()
                setSelectedText()
                handleResetButtonState()
                notifyOnResetClicked(v)
                if (showController) updateControllerButtons()
            }

            setTag(R.id.tag_slider_reset_listener_set, "set")
        }
    }
}
