package com.drdisagree.iconify.ui.fragments.xposed

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import com.drdisagree.iconify.BuildConfig
import com.drdisagree.iconify.Iconify.Companion.appContext
import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.common.Preferences.LSCLOCK_STYLE
import com.drdisagree.iconify.data.common.Preferences.LSCLOCK_SWITCH
import com.drdisagree.iconify.data.common.Resources.LOCKSCREEN_CLOCK_LAYOUT
import com.drdisagree.iconify.data.common.Resources.searchableFragments
import com.drdisagree.iconify.data.config.RPrefs.getBoolean
import com.drdisagree.iconify.data.config.RPrefs.getInt
import com.drdisagree.iconify.data.config.RPrefs.putBoolean
import com.drdisagree.iconify.data.config.RPrefs.putInt
import com.drdisagree.iconify.databinding.FragmentXposedLockscreenClockBinding
import com.drdisagree.iconify.ui.activities.MainActivity.Companion.replaceFragment
import com.drdisagree.iconify.ui.base.BaseFragment
import com.drdisagree.iconify.ui.base.ControlledPreferenceFragmentCompat
import com.drdisagree.iconify.data.models.ClockCarouselItemModel
import com.drdisagree.iconify.ui.preferences.preferencesearch.SearchPreferenceResult
import com.drdisagree.iconify.ui.utils.ViewHelper.setHeader
import com.drdisagree.iconify.ui.views.ClockCarouselView
import com.drdisagree.iconify.utils.SystemUtils
import com.drdisagree.iconify.utils.WallpaperUtils.loadWallpaper
import com.google.android.material.color.DynamicColors
import com.topjohnwu.superuser.internal.UiThreadHandler.handler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors


class LockscreenClockParent : BaseFragment() {

    private lateinit var binding: FragmentXposedLockscreenClockBinding
    private var updateRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SystemUtils.hasStoragePermission()) {
            SystemUtils.requestStoragePermission(requireContext())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentXposedLockscreenClockBinding.inflate(inflater, container, false)

        // Header
        setHeader(
            requireContext(),
            getParentFragmentManager(),
            binding.header.toolbar,
            R.string.activity_title_lockscreen_clock
        )

        childFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, lockscreenClockFragment)
            .commit()

        binding.lsClockSwitch.isSwitchChecked = getBoolean(LSCLOCK_SWITCH, false)
        binding.lsClockSwitch.setSwitchChangeListener { _: CompoundButton?, isChecked: Boolean ->
            putBoolean(LSCLOCK_SWITCH, isChecked)
            lockscreenClockFragment.updateScreen(LSCLOCK_SWITCH)
        }

        loadAndSetWallpaper()

        return binding.root
    }

    @SuppressLint("DiscouragedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.clockCarouselView.clockCarouselViewStub.layoutResource =
            R.layout.clock_carousel_view
        val clockCarouselView =
            binding.clockCarouselView.clockCarouselViewStub.inflate() as ClockCarouselView

        if (context != null) {
            val dynamicColorContext = DynamicColors.wrapContextIfAvailable(
                requireContext(),
                R.style.ThemeOverlay_Material3_DynamicColors_Dark
            )
            val attrsToResolve = intArrayOf(R.attr.colorSurfaceContainer)
            val typedArray = dynamicColorContext.obtainStyledAttributes(attrsToResolve)
            val colorSurfaceContainer = typedArray.getColor(0, 0)
            typedArray.recycle()
            clockCarouselView.setCarouselCardColor(colorSurfaceContainer)
        }

        Executors.newSingleThreadExecutor().execute {
            if (context == null) return@execute

            val lsClock: MutableList<ClockCarouselItemModel> = ArrayList()
            var maxIndex = 0
            while (resources.getIdentifier(
                    LOCKSCREEN_CLOCK_LAYOUT + maxIndex,
                    "layout",
                    BuildConfig.APPLICATION_ID
                ) != 0
            ) {
                if (context == null) return@execute

                lsClock.add(
                    ClockCarouselItemModel(
                        if (maxIndex == 0) getString(R.string.clock_none)
                        else getString(R.string.clock_style_name, maxIndex),
                        maxIndex,
                        getInt(LSCLOCK_STYLE, 0) == maxIndex,
                        LOCKSCREEN_CLOCK_LAYOUT + maxIndex,
                        LayoutInflater.from(appContext).inflate(
                            resources.getIdentifier(
                                LOCKSCREEN_CLOCK_LAYOUT + maxIndex,
                                "layout",
                                BuildConfig.APPLICATION_ID
                            ),
                            null
                        ).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                gravity = Gravity.CENTER_HORIZONTAL
                            }
                        }
                    )
                )
                maxIndex++

                if (context == null) return@execute
            }

            Handler(Looper.getMainLooper()).postDelayed({
                clockCarouselView.setUpClockCarouselView(lsClock) { onClockSelected ->
                    updateRunnable?.let {
                        handler.removeCallbacks(it)
                    }
                    updateRunnable = Runnable {
                        putInt(LSCLOCK_STYLE, onClockSelected.clockLayout)
                    }
                    updateRunnable?.let {
                        handler.postDelayed(it, 500)
                    }
                }

                binding.clockCarouselView.screenPreviewClickView.setOnSideClickedListener { isStart ->
                    if (isStart) clockCarouselView.scrollToPrevious()
                    else clockCarouselView.scrollToNext()
                }
            }, 50)
        }
    }

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        for (searchableFragment in searchableFragments) {
            if (searchableFragment.xml == result.resourceFile) {
                replaceFragment(parentFragmentManager, searchableFragment.fragment)
                scrollToPreference()
                SearchPreferenceResult.highlight(lockscreenClockFragment, result.key);
                break
            }
        }
    }

    private fun loadAndSetWallpaper() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            if (context == null) return@launch

            val bitmap = loadWallpaper(requireContext(), isLockscreen = true).await()

            binding.clockCarouselView.preview.wallpaperDimmingScrim.visibility = View.VISIBLE
            binding.clockCarouselView.preview.wallpaperFadeinScrim.visibility = View.VISIBLE
            binding.clockCarouselView.preview.wallpaperPreviewSpinner.visibility = View.GONE

            if (bitmap != null) {
                binding.clockCarouselView.preview.wallpaperFadeinScrim.setImageBitmap(bitmap)
            } else {
                binding.clockCarouselView.preview.wallpaperFadeinScrim.setImageResource(R.drawable.google_pixel_wallpaper)
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onResume() {
        super.onResume()
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        loadAndSetWallpaper()
    }

    override fun onPause() {
        super.onPause()
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    fun scrollToPreference() {
        Handler(Looper.getMainLooper()).postDelayed({
            binding.scrollView.smoothScrollTo(0, binding.fragmentContainer.top)
        }, 180)
    }

    companion object {

        private val lockscreenClockFragment = LockscreenClock()

        fun getPreferenceFragment(): ControlledPreferenceFragmentCompat {
            return lockscreenClockFragment
        }
    }
}