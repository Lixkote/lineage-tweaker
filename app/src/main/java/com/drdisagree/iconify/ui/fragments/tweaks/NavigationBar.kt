package com.drdisagree.iconify.ui.fragments.tweaks

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.annotation.IntRange
import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.common.Const.FRAMEWORK_PACKAGE
import com.drdisagree.iconify.data.common.Const.LAUNCHER3_PACKAGE
import com.drdisagree.iconify.data.common.Const.PIXEL_LAUNCHER_PACKAGE
import com.drdisagree.iconify.data.common.Const.SWITCH_ANIMATION_DELAY
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.common.Preferences.NAVBAR_FULL_SCREEN
import com.drdisagree.iconify.data.common.Preferences.NAVBAR_GCAM_LAG_FIX
import com.drdisagree.iconify.data.common.Preferences.NAVBAR_HIDE_PILL
import com.drdisagree.iconify.data.common.Preferences.NAVBAR_IMMERSIVE_V1
import com.drdisagree.iconify.data.common.Preferences.NAVBAR_IMMERSIVE_V2
import com.drdisagree.iconify.data.common.Preferences.NAVBAR_IMMERSIVE_V3
import com.drdisagree.iconify.data.common.Preferences.NAVBAR_LOW_SENS
import com.drdisagree.iconify.data.common.Preferences.PILL_SHAPE_SWITCH
import com.drdisagree.iconify.data.common.References.FABRICATED_PILL_BOTTOM_SPACE
import com.drdisagree.iconify.data.common.References.FABRICATED_PILL_THICKNESS
import com.drdisagree.iconify.data.common.References.FABRICATED_PILL_WIDTH
import com.drdisagree.iconify.data.config.RPrefs.getBoolean
import com.drdisagree.iconify.data.config.RPrefs.getInt
import com.drdisagree.iconify.data.config.RPrefs.putBoolean
import com.drdisagree.iconify.data.config.RPrefs.putInt
import com.drdisagree.iconify.data.database.DynamicResourceDatabase
import com.drdisagree.iconify.data.repository.DynamicResourceRepository
import com.drdisagree.iconify.databinding.FragmentNavigationBarBinding
import com.drdisagree.iconify.ui.base.BaseFragment
import com.drdisagree.iconify.ui.utils.ViewHelper.setHeader
import com.drdisagree.iconify.utils.SystemUtils
import com.drdisagree.iconify.utils.SystemUtils.hasStoragePermission
import com.drdisagree.iconify.utils.SystemUtils.requestStoragePermission
import com.drdisagree.iconify.utils.SystemUtils.restartSystemUI
import com.drdisagree.iconify.utils.overlay.OverlayUtils
import com.drdisagree.iconify.utils.overlay.OverlayUtils.enableOverlay
import com.drdisagree.iconify.utils.overlay.manager.resource.ResourceEntry
import com.drdisagree.iconify.utils.overlay.manager.resource.ResourceManager.buildOverlayWithResource
import com.drdisagree.iconify.utils.overlay.manager.resource.ResourceManager.removeResourceFromOverlay
import com.drdisagree.iconify.utils.overlay.manager.resource.ResourceManager.removeResources
import com.google.android.material.slider.Slider
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class NavigationBar : BaseFragment() {

    private lateinit var binding: FragmentNavigationBarBinding
    private var isAtleastA14 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    private val repository = DynamicResourceRepository(
        DynamicResourceDatabase.getInstance().dynamicResourceDao()
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNavigationBarBinding.inflate(inflater, container, false)
        val view: View = binding.getRoot()

        // Header
        setHeader(
            requireContext(),
            getParentFragmentManager(),
            binding.header.toolbar,
            R.string.activity_title_navigation_bar
        )

        // Switch states
        binding.nbFullscreen.isSwitchChecked = getBoolean(NAVBAR_FULL_SCREEN)
        binding.nbImmersive.isSwitchChecked = getBoolean(NAVBAR_IMMERSIVE_V1)
        binding.nbImmersiveV2.isSwitchChecked = getBoolean(NAVBAR_IMMERSIVE_V2)
        binding.nbImmersiveV3.isSwitchChecked = getBoolean(NAVBAR_IMMERSIVE_V3)
        binding.nbGcamLagFix.isSwitchChecked = getBoolean(NAVBAR_GCAM_LAG_FIX)
        binding.nbLowerSens.isSwitchChecked = getBoolean(NAVBAR_LOW_SENS)
        binding.nbHidePill.isSwitchChecked = getBoolean(NAVBAR_HIDE_PILL)
        binding.nbMonetPill.isSwitchChecked = getBoolean("IconifyComponentNBMonetPill.overlay")
        binding.nbHideKbButtons.isSwitchChecked = getBoolean("NBHideKBButton")

        binding.nbDisableLeftGesture.isSwitchChecked = isLeftGestureDisabled
        binding.nbDisableRightGesture.isSwitchChecked = isRightGestureDisabled

        binding.nbHidePill.setEnabled(!binding.nbFullscreen.isSwitchChecked)
        binding.nbMonetPill.setEnabled(!binding.nbHidePill.isSwitchChecked && !binding.nbFullscreen.isSwitchChecked)

        // Fullscreen
        val nbFullScreenClicked = AtomicBoolean(false)

        binding.nbFullscreen.setSwitchChangeListener { buttonView: CompoundButton, isSwitchChecked: Boolean ->
            if (buttonView.isPressed || nbFullScreenClicked.get()) {
                nbFullScreenClicked.set(false)

                if (!hasStoragePermission()) {
                    requestStoragePermission(requireContext())
                    binding.nbFullscreen.isSwitchChecked = !isSwitchChecked

                    return@setSwitchChangeListener
                }

                binding.nbHidePill.setEnabled(!isSwitchChecked)
                binding.nbMonetPill.setEnabled(!isSwitchChecked && !binding.nbHidePill.isSwitchChecked)

                if (isSwitchChecked) {
                    binding.pillShape.pillShapeContainer.visibility = View.GONE
                } else {
                    binding.pillShape.pillShapeContainer.visibility = View.VISIBLE
                }

                CoroutineScope(Dispatchers.IO).launch {
                    disableOthers(NAVBAR_FULL_SCREEN)
                    delay(SWITCH_ANIMATION_DELAY)
                    handleFullScreen(isSwitchChecked)
                }
            }
        }
        binding.nbFullscreen.setBeforeSwitchChangeListener {
            nbFullScreenClicked.set(
                true
            )
        }

        // Immersive
        val nbImmersiveClicked = AtomicBoolean(false)

        binding.nbImmersive.setSwitchChangeListener { buttonView: CompoundButton, isSwitchChecked: Boolean ->
            if (buttonView.isPressed || nbImmersiveClicked.get()) {
                nbImmersiveClicked.set(false)

                if (!hasStoragePermission()) {
                    requestStoragePermission(requireContext())
                    binding.nbImmersive.isSwitchChecked = !isSwitchChecked

                    return@setSwitchChangeListener
                }

                CoroutineScope(Dispatchers.IO).launch {
                    disableOthers(NAVBAR_IMMERSIVE_V1)
                    delay(SWITCH_ANIMATION_DELAY)
                    handleImmersive(isSwitchChecked, 1)
                }
            }
        }
        binding.nbImmersive.setBeforeSwitchChangeListener {
            nbImmersiveClicked.set(
                true
            )
        }

        // Immersive V2
        val nbImmersiveV2Clicked = AtomicBoolean(false)
        binding.nbImmersiveV2.setSwitchChangeListener { buttonView: CompoundButton, isSwitchChecked: Boolean ->
            if (buttonView.isPressed || nbImmersiveV2Clicked.get()) {
                nbImmersiveV2Clicked.set(false)

                if (!hasStoragePermission()) {
                    requestStoragePermission(requireContext())
                    binding.nbImmersiveV2.isSwitchChecked = !isSwitchChecked

                    return@setSwitchChangeListener
                }

                CoroutineScope(Dispatchers.IO).launch {
                    disableOthers(NAVBAR_IMMERSIVE_V2)
                    delay(SWITCH_ANIMATION_DELAY)
                    handleImmersive(isSwitchChecked, 2)
                }
            }
        }
        binding.nbImmersiveV2.setBeforeSwitchChangeListener {
            nbImmersiveV2Clicked.set(
                true
            )
        }
        binding.nbImmersiveV2.visibility = if (isAtleastA14) View.GONE else View.VISIBLE

        // Immersive V3
        val nbImmersiveV3Clicked = AtomicBoolean(false)
        binding.nbImmersiveV3.setSwitchChangeListener { buttonView: CompoundButton, isSwitchChecked: Boolean ->
            if (buttonView.isPressed || nbImmersiveV3Clicked.get()) {
                nbImmersiveV3Clicked.set(false)

                if (!hasStoragePermission()) {
                    requestStoragePermission(requireContext())
                    binding.nbImmersiveV3.isSwitchChecked = !isSwitchChecked

                    return@setSwitchChangeListener
                }

                CoroutineScope(Dispatchers.IO).launch {
                    disableOthers(NAVBAR_IMMERSIVE_V3)
                    delay(SWITCH_ANIMATION_DELAY)
                    handleImmersive(isSwitchChecked, 3)
                }
            }
        }
        binding.nbImmersiveV3.setBeforeSwitchChangeListener {
            nbImmersiveV3Clicked.set(
                true
            )
        }
        binding.nbImmersiveV3.visibility = if (isAtleastA14) View.GONE else View.VISIBLE

        // GCam Lag Fix
        val nbGcamLagFixClicked = AtomicBoolean(false)
        binding.nbGcamLagFix.setSwitchChangeListener { buttonView: CompoundButton, isSwitchChecked: Boolean ->
            if (buttonView.isPressed || nbGcamLagFixClicked.get()) {
                nbGcamLagFixClicked.set(false)

                if (!hasStoragePermission()) {
                    requestStoragePermission(requireContext())
                    binding.nbGcamLagFix.isSwitchChecked = !isSwitchChecked
                    return@setSwitchChangeListener
                }

                putBoolean(NAVBAR_GCAM_LAG_FIX, isSwitchChecked)

                val fullscreen = getBoolean(NAVBAR_FULL_SCREEN)
                val immersive1 = getBoolean(NAVBAR_IMMERSIVE_V1)
                val immersive2 = getBoolean(NAVBAR_IMMERSIVE_V2)
                val immersive3 = getBoolean(NAVBAR_IMMERSIVE_V3)

                CoroutineScope(Dispatchers.IO).launch {
                    delay(SWITCH_ANIMATION_DELAY)
                    if (fullscreen) {
                        handleFullScreen(true)
                    } else if (immersive1) {
                        handleImmersive(true, 1)
                    } else if (immersive2) {
                        handleImmersive(true, 2)
                    } else if (immersive3) {
                        handleImmersive(true, 3)
                    }
                }
            }
        }
        binding.nbGcamLagFix.setBeforeSwitchChangeListener {
            nbGcamLagFixClicked.set(
                true
            )
        }

        // Lower Sensitivity
        val nbLowerSensClicked = AtomicBoolean(false)

        binding.nbLowerSens.setSwitchChangeListener { buttonView: CompoundButton, isSwitchChecked: Boolean ->
            if (buttonView.isPressed || nbLowerSensClicked.get()) {
                nbLowerSensClicked.set(false)

                if (!hasStoragePermission()) {
                    requestStoragePermission(requireContext())
                    binding.nbLowerSens.isSwitchChecked = !isSwitchChecked

                    return@setSwitchChangeListener
                }

                CoroutineScope(Dispatchers.IO).launch {
                    delay(SWITCH_ANIMATION_DELAY)
                    handleLowSensitivity(isSwitchChecked)
                }
            }
        }
        binding.nbLowerSens.setBeforeSwitchChangeListener {
            nbLowerSensClicked.set(
                true
            )
        }

        // Hide Pill
        val nbHidePillClicked = AtomicBoolean(false)

        binding.nbHidePill.setSwitchChangeListener { buttonView: CompoundButton, isSwitchChecked: Boolean ->
            if (buttonView.isPressed || nbHidePillClicked.get()) {
                nbHidePillClicked.set(false)

                if (!hasStoragePermission()) {
                    requestStoragePermission(requireContext())
                    binding.nbHidePill.isSwitchChecked = !isSwitchChecked

                    return@setSwitchChangeListener
                }

                CoroutineScope(Dispatchers.IO).launch {
                    delay(SWITCH_ANIMATION_DELAY)

                    handleHidePill(isSwitchChecked)

                    delay(2000)
                    SystemUtils.handleSystemUIRestart()
                }
            }
        }
        binding.nbHidePill.setBeforeSwitchChangeListener {
            nbHidePillClicked.set(
                true
            )
        }

        // Monet Pill
        binding.nbMonetPill.setSwitchChangeListener { _: CompoundButton?, isSwitchChecked: Boolean ->
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    if (isSwitchChecked) {
                        enableOverlay("IconifyComponentNBMonetPill.overlay")
                        restartSystemUI()
                    } else {
                        OverlayUtils.disableOverlay("IconifyComponentNBMonetPill.overlay")
                        restartSystemUI()
                    }
                }, SWITCH_ANIMATION_DELAY
            )
        }

        // Hide Keyboard Buttons
        binding.nbHideKbButtons.setSwitchChangeListener { _: CompoundButton?, isSwitchChecked: Boolean ->
            CoroutineScope(Dispatchers.IO).launch {
                delay(SWITCH_ANIMATION_DELAY)
                putBoolean("NBHideKBButton", isSwitchChecked)

                val resource = mutableListOf(
                    ResourceEntry(
                        SYSTEMUI_PACKAGE,
                        "string",
                        "config_navBarLayoutHandle",
                        ";home_handle;"
                    ).apply {
                        isPortrait = true
                        isLandscape = true
                    },
                    ResourceEntry(
                        SYSTEMUI_PACKAGE,
                        "string",
                        "config_navBarLayout",
                        ""
                    ).apply {
                        isPortrait = true
                        isLandscape = true
                    }
                ).apply {
                    if (isAtleastA14) {
                        add(
                            ResourceEntry(
                                FRAMEWORK_PACKAGE,
                                "bool",
                                "config_imeDrawsImeNavBar",
                                "false"
                            )
                        )
                    }
                }

                if (isSwitchChecked) {
                    buildOverlayWithResource(*resource.toTypedArray())
                } else {
                    removeResourceFromOverlay(*resource.toTypedArray())
                }
            }
        }

        // Disable left gesture
        binding.nbDisableLeftGesture.setSwitchChangeListener { _: CompoundButton?, isSwitchChecked: Boolean ->
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    if (isSwitchChecked) {
                        Shell.cmd(
                            "settings put secure back_gesture_inset_scale_left -1 &>/dev/null"
                        ).exec()
                    } else {
                        Shell.cmd(
                            "settings delete secure back_gesture_inset_scale_left &>/dev/null"
                        ).exec()
                    }
                }, SWITCH_ANIMATION_DELAY
            )
        }

        // Disable right gesture
        binding.nbDisableRightGesture.setSwitchChangeListener { _: CompoundButton?, isSwitchChecked: Boolean ->
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    if (isSwitchChecked) {
                        Shell.cmd(
                            "settings put secure back_gesture_inset_scale_right -1 &>/dev/null"
                        ).exec()
                    } else {
                        Shell.cmd(
                            "settings delete secure back_gesture_inset_scale_right &>/dev/null"
                        ).exec()
                    }
                }, SWITCH_ANIMATION_DELAY
            )
        }

        // Pill shape
        binding.pillShape.pillShapeContainer.visibility =
            if (binding.nbFullscreen.isSwitchChecked || binding.nbHidePill.isSwitchChecked) {
                View.GONE
            } else {
                View.VISIBLE
            }

        // Pill width
        val finalPillWidth = intArrayOf(getInt(FABRICATED_PILL_WIDTH, 108))
        binding.pillShape.pillWidth.sliderValue = finalPillWidth[0]
        binding.pillShape.pillWidth.setOnSliderTouchListener(object :
            Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                finalPillWidth[0] = slider.value.toInt()
            }
        })

        // Pill thickness
        val finalPillThickness = intArrayOf(getInt(FABRICATED_PILL_THICKNESS, 2))
        binding.pillShape.pillThickness.sliderValue = finalPillThickness[0]
        binding.pillShape.pillThickness.setOnSliderTouchListener(object :
            Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                finalPillThickness[0] = slider.value.toInt()
            }
        })

        // Bottom space
        val finalBottomSpace = intArrayOf(getInt(FABRICATED_PILL_BOTTOM_SPACE, 6))
        binding.pillShape.pillBottomSpace.sliderValue = finalBottomSpace[0]
        binding.pillShape.pillBottomSpace.setOnSliderTouchListener(object :
            Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                finalBottomSpace[0] = slider.value.toInt()
            }
        })

        // Apply button
        fun getPillShapeResources(
            pillWidth: Int,
            pillThickness: Int,
            bottomSpace: Int
        ) = mutableListOf(
            ResourceEntry(
                SYSTEMUI_PACKAGE,
                "dimen",
                "navigation_home_handle_width",
                pillWidth.toString() + "dp"
            ),
            ResourceEntry(
                SYSTEMUI_PACKAGE,
                "dimen",
                "navigation_handle_radius",
                pillThickness.toString() + "dp"
            ),
            ResourceEntry(
                SYSTEMUI_PACKAGE,
                "dimen",
                "navigation_handle_bottom",
                bottomSpace.toString() + "dp"
            )
        ).apply {
            if (isAtleastA14) {
                addAll(
                    listOf(
                        ResourceEntry(
                            PIXEL_LAUNCHER_PACKAGE,
                            "dimen",
                            "taskbar_stashed_handle_width",
                            pillWidth.toString() + "dp"
                        ),
                        ResourceEntry(
                            LAUNCHER3_PACKAGE,
                            "dimen",
                            "taskbar_stashed_handle_width",
                            pillWidth.toString() + "dp"
                        )
                    )
                )
            }
        }
        binding.pillShape.pillShapeApply.setOnClickListener {
            if (!hasStoragePermission()) {
                requestStoragePermission(requireContext())

                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.IO).launch {
                putBoolean(PILL_SHAPE_SWITCH, true)

                putInt(FABRICATED_PILL_WIDTH, finalPillWidth[0])
                putInt(FABRICATED_PILL_THICKNESS, finalPillThickness[0])
                putInt(FABRICATED_PILL_BOTTOM_SPACE, finalBottomSpace[0])

                buildOverlayWithResource(
                    *getPillShapeResources(
                        pillWidth = finalPillWidth[0],
                        pillThickness = finalPillThickness[0],
                        bottomSpace = finalBottomSpace[0]
                    ).toTypedArray()
                )

                withContext(Dispatchers.Main) {
                    binding.pillShape.pillShapeReset.visibility = View.VISIBLE
                }

                delay(SWITCH_ANIMATION_DELAY)
                restartSystemUI()
            }
        }

        // Reset button
        binding.pillShape.pillShapeReset.visibility =
            if (getBoolean(PILL_SHAPE_SWITCH)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        binding.pillShape.pillShapeReset.setOnClickListener {
            if (!hasStoragePermission()) {
                requestStoragePermission(requireContext())

                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.IO).launch {
                putBoolean(PILL_SHAPE_SWITCH, false)

                removeResourceFromOverlay(
                    *getPillShapeResources(
                        pillWidth = finalPillWidth[0],
                        pillThickness = finalPillThickness[0],
                        bottomSpace = finalBottomSpace[0]
                    ).toTypedArray()
                )

                withContext(Dispatchers.Main) {
                    binding.pillShape.pillShapeReset.visibility = View.GONE
                }

                delay(SWITCH_ANIMATION_DELAY)
                restartSystemUI()
            }
        }
        return view
    }

    private val isLeftGestureDisabled: Boolean
        get() = try {
            Shell.cmd(
                "settings get secure back_gesture_inset_scale_left"
            ).exec().out[0].toInt() == -1
        } catch (ignored: Exception) {
            false
        }

    private val isRightGestureDisabled: Boolean
        get() = try {
            Shell.cmd(
                "settings get secure back_gesture_inset_scale_right"
            ).exec().out[0].toInt() == -1
        } catch (ignored: Exception) {
            false
        }

    private suspend fun disableOthers(identifier: String) {
        if (identifier != NAVBAR_FULL_SCREEN) {
            putBoolean(NAVBAR_FULL_SCREEN, false)
            withContext(Dispatchers.Main) {
                binding.nbFullscreen.isSwitchChecked = false
            }
            removeResources(*getFullScreenResources("", "").toTypedArray())
        }

        if (identifier != NAVBAR_IMMERSIVE_V1) {
            putBoolean(NAVBAR_IMMERSIVE_V1, false)
            withContext(Dispatchers.Main) {
                binding.nbImmersive.isSwitchChecked = false
            }
            removeResources(*getImmersiveResources("", "").toTypedArray())
        }

        if (identifier != NAVBAR_IMMERSIVE_V2) {
            putBoolean(NAVBAR_IMMERSIVE_V2, false)
            withContext(Dispatchers.Main) {
                binding.nbImmersiveV2.isSwitchChecked = false
            }
            removeResources(*getImmersiveResources("", "").toTypedArray())
        }

        if (identifier != NAVBAR_IMMERSIVE_V3) {
            putBoolean(NAVBAR_IMMERSIVE_V3, false)
            withContext(Dispatchers.Main) {
                binding.nbImmersiveV3.isSwitchChecked = false
            }
            removeResources(*getImmersiveResources("", "").toTypedArray())
        }
    }

    private fun handleFullScreen(enable: Boolean) {
        putBoolean(NAVBAR_FULL_SCREEN, enable)

        val gcamLagFix = getBoolean(NAVBAR_GCAM_LAG_FIX)
        val barHeight = if (gcamLagFix) "0.3dp" else "0dp"
        val frameHeight = if (gcamLagFix) "0.1dp" else "0dp"

        val fullScreenResources = getFullScreenResources(barHeight, frameHeight)

        CoroutineScope(Dispatchers.IO).launch {
            if (enable) {
                buildOverlayWithResource(*fullScreenResources.toTypedArray())
            } else {
                removeResourceFromOverlay(*fullScreenResources.toTypedArray())
            }
        }
    }

    private fun getFullScreenResources(
        barHeight: String,
        frameHeight: String
    ): List<ResourceEntry> {
        return mutableListOf(
            ResourceEntry(
                FRAMEWORK_PACKAGE,
                "bool",
                "config_imeDrawsImeNavBar",
                "false"
            ),
            ResourceEntry(
                FRAMEWORK_PACKAGE,
                "dimen",
                "navigation_bar_height",
                barHeight
            ),
            ResourceEntry(
                FRAMEWORK_PACKAGE,
                "dimen",
                "navigation_bar_frame_height",
                frameHeight
            )
        ).apply {
            if (isAtleastA14) {
                addAll(
                    listOf(
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "dimen",
                            "navigation_bar_width",
                            "0dp"
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "dimen",
                            "navigation_bar_height_portrait",
                            barHeight
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "dimen",
                            "navigation_bar_height_landscape",
                            barHeight
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "dimen",
                            "navigation_bar_frame_height_landscape",
                            frameHeight
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "bool",
                            "config_allowSeamlessRotationDespiteNavBarMoving",
                            "true"
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "bool",
                            "config_navBarAlwaysShowOnSideEdgeGesture",
                            "true"
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "bool",
                            "config_navBarCanMove",
                            "false"
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "bool",
                            "config_navBarTapThrough",
                            "true"
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "dimen",
                            "config_backGestureInset",
                            "24dp"
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "dimen",
                            "navigation_bar_gesture_height",
                            "24dp"
                        ),
                        ResourceEntry(
                            PIXEL_LAUNCHER_PACKAGE,
                            "dimen",
                            "taskbar_stashed_handle_height",
                            "0dp"
                        ),
                        ResourceEntry(
                            LAUNCHER3_PACKAGE,
                            "dimen",
                            "taskbar_stashed_handle_height",
                            "0dp"
                        )
                    )
                )
            }
        }
    }

    private fun handleImmersive(enable: Boolean, @IntRange(from = 1, to = 3) version: Int) {
        when (version) {
            1 -> putBoolean(NAVBAR_IMMERSIVE_V1, enable)
            2 -> putBoolean(NAVBAR_IMMERSIVE_V2, enable)
            3 -> putBoolean(NAVBAR_IMMERSIVE_V3, enable)
        }

        val gcamLagFix = getBoolean(NAVBAR_GCAM_LAG_FIX)
        val barHeight = if (gcamLagFix) "0.3dp" else "0dp"
        val frameHeight = if (version == 1) "48dp" else if (version == 2) "26dp" else "16dp"

        val resources = getImmersiveResources(barHeight, frameHeight)

        CoroutineScope(Dispatchers.IO).launch {
            if (enable) {
                buildOverlayWithResource(*resources.toTypedArray())
            } else {
                removeResourceFromOverlay(*resources.toTypedArray())
            }
        }
    }

    private fun getImmersiveResources(
        barHeight: String,
        frameHeight: String
    ): List<ResourceEntry> {
        return mutableListOf(
            ResourceEntry(
                FRAMEWORK_PACKAGE,
                "dimen",
                "navigation_bar_height",
                barHeight
            ),
            ResourceEntry(
                FRAMEWORK_PACKAGE,
                "dimen",
                "navigation_bar_frame_height",
                frameHeight
            )
        ).apply {
            if (isAtleastA14) {
                addAll(
                    listOf(
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "dimen",
                            "navigation_bar_width",
                            "0dp"
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "dimen",
                            "navigation_bar_height_portrait",
                            barHeight
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "dimen",
                            "navigation_bar_height_landscape",
                            barHeight
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "dimen",
                            "navigation_bar_frame_height_landscape",
                            frameHeight
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "bool",
                            "config_allowSeamlessRotationDespiteNavBarMoving",
                            "true"
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "bool",
                            "config_navBarAlwaysShowOnSideEdgeGesture",
                            "true"
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "bool",
                            "config_navBarCanMove",
                            "false"
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "bool",
                            "config_navBarTapThrough",
                            "true"
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "dimen",
                            "config_backGestureInset",
                            "24dp"
                        ),
                        ResourceEntry(
                            FRAMEWORK_PACKAGE,
                            "dimen",
                            "navigation_bar_gesture_height",
                            "24dp"
                        ),
                        ResourceEntry(
                            PIXEL_LAUNCHER_PACKAGE,
                            "dimen",
                            "taskbar_stashed_handle_height",
                            "0dp"
                        ),
                        ResourceEntry(
                            LAUNCHER3_PACKAGE,
                            "dimen",
                            "taskbar_stashed_handle_height",
                            "0dp"
                        )
                    )
                )
            } else {
                add(
                    ResourceEntry(
                        FRAMEWORK_PACKAGE,
                        "bool",
                        "config_imeDrawsImeNavBar",
                        "false"
                    )
                )
            }
        }
    }

    private fun handleLowSensitivity(enable: Boolean) {
        putBoolean(NAVBAR_LOW_SENS, enable)

        val resources = listOf(
            ResourceEntry(FRAMEWORK_PACKAGE, "dimen", "navigation_bar_gesture_height", "12dp")
        )

        CoroutineScope(Dispatchers.IO).launch {
            if (enable) {
                buildOverlayWithResource(*resources.toTypedArray())
            } else {
                removeResourceFromOverlay(*resources.toTypedArray())
            }
        }
    }

    private fun handleHidePill(enable: Boolean) {
        putBoolean(NAVBAR_HIDE_PILL, enable)

        val resources = mutableListOf(
            ResourceEntry(
                SYSTEMUI_PACKAGE,
                "dimen",
                "navigation_handle_radius",
                "0dp"
            ),
            ResourceEntry(
                SYSTEMUI_PACKAGE,
                "dimen",
                "navigation_home_handle_width",
                "0dp"
            )
        ).apply {
            if (isAtleastA14) {
                addAll(
                    listOf(
                        ResourceEntry(
                            SYSTEMUI_PACKAGE,
                            "dimen",
                            "navigation_handle_horizontal_margin",
                            "0dp"
                        ),
                        ResourceEntry(
                            SYSTEMUI_PACKAGE,
                            "dimen",
                            "navigation_handle_sample_horizontal_margin",
                            "0dp"
                        ),
                        ResourceEntry(
                            SYSTEMUI_PACKAGE,
                            "dimen",
                            "navigation_home_handle_width_land",
                            "0dp"
                        ),
                        ResourceEntry(
                            PIXEL_LAUNCHER_PACKAGE,
                            "dimen",
                            "transient_taskbar_stashed_height",
                            "0.2dp"
                        ),
                        ResourceEntry(
                            PIXEL_LAUNCHER_PACKAGE,
                            "dimen",
                            "taskbar_from_nav_threshold",
                            "10dp"
                        ),
                        ResourceEntry(
                            PIXEL_LAUNCHER_PACKAGE,
                            "dimen",
                            "taskbar_stashed_size",
                            "0.2dp"
                        ),
                        ResourceEntry(
                            PIXEL_LAUNCHER_PACKAGE,
                            "dimen",
                            "taskbar_suw_insets",
                            "0.1dp"
                        ),
                        ResourceEntry(
                            LAUNCHER3_PACKAGE,
                            "dimen",
                            "transient_taskbar_stashed_height",
                            "0.2dp"
                        ),
                        ResourceEntry(
                            LAUNCHER3_PACKAGE,
                            "dimen",
                            "taskbar_from_nav_threshold",
                            "10dp"
                        ),
                        ResourceEntry(
                            LAUNCHER3_PACKAGE,
                            "dimen",
                            "taskbar_stashed_size",
                            "0.2dp"
                        ),
                        ResourceEntry(
                            LAUNCHER3_PACKAGE,
                            "dimen",
                            "taskbar_suw_insets",
                            "0.1dp"
                        ),
                        ResourceEntry(
                            PIXEL_LAUNCHER_PACKAGE,
                            "dimen",
                            "taskbar_nav_buttons_size",
                            "0dp"
                        ),
                        ResourceEntry(
                            PIXEL_LAUNCHER_PACKAGE,
                            "dimen",
                            "taskbar_stashed_handle_height",
                            "0dp"
                        ),
                        ResourceEntry(
                            LAUNCHER3_PACKAGE,
                            "dimen",
                            "taskbar_nav_buttons_size",
                            "0dp"
                        ),
                        ResourceEntry(
                            LAUNCHER3_PACKAGE,
                            "dimen",
                            "taskbar_stashed_handle_height",
                            "0dp"
                        )
                    )
                )
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            if (enable) {
                buildOverlayWithResource(*resources.toTypedArray())
            } else {
                removeResourceFromOverlay(*resources.toTypedArray())
            }
        }
    }
}