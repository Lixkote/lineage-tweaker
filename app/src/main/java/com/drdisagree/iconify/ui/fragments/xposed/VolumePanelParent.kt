package com.drdisagree.iconify.ui.fragments.xposed

import android.annotation.SuppressLint
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.drdisagree.iconify.Iconify.Companion.appContext
import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.common.Preferences.VOLUME_PANEL_PERCENTAGE
import com.drdisagree.iconify.data.common.Preferences.VOLUME_PANEL_STYLE
import com.drdisagree.iconify.data.common.Resources.searchableFragments
import com.drdisagree.iconify.data.config.RPrefs
import com.drdisagree.iconify.data.config.RPrefs.getPrefs
import com.drdisagree.iconify.databinding.FragmentXposedVolumePanelBinding
import com.drdisagree.iconify.ui.activities.MainActivity.Companion.replaceFragment
import com.drdisagree.iconify.ui.base.BaseFragment
import com.drdisagree.iconify.ui.base.ControlledPreferenceFragmentCompat
import com.drdisagree.iconify.ui.preferences.preferencesearch.SearchPreferenceResult
import com.drdisagree.iconify.ui.utils.ViewHelper.setHeader

class VolumePanelParent : BaseFragment() {

    private lateinit var binding: FragmentXposedVolumePanelBinding

    val listener = OnSharedPreferenceChangeListener { prefs, key ->
        if (key == VOLUME_PANEL_STYLE) {
            updateVolumePreview(prefs.getString(key, "0")!!.toInt())
        } else if (key == VOLUME_PANEL_PERCENTAGE) {
            if (prefs.getBoolean(key, false)) {
                binding.volumeThickBg.volumeNumber.visibility = View.VISIBLE
            } else {
                binding.volumeThickBg.volumeNumber.visibility = View.GONE
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentXposedVolumePanelBinding.inflate(inflater, container, false)

        // Header
        setHeader(
            requireContext(),
            getParentFragmentManager(),
            binding.header.toolbar,
            R.string.activity_title_volume_panel
        )

        childFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, volumeStyleFragment)
            .commit()

        return binding.root
    }

    @SuppressLint("DiscouragedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateVolumePreview(RPrefs.getString(VOLUME_PANEL_STYLE, "0")!!.toInt())

        if (RPrefs.getBoolean(VOLUME_PANEL_PERCENTAGE, false)) {
            binding.volumeThickBg.volumeNumber.visibility = View.VISIBLE
        } else {
            binding.volumeThickBg.volumeNumber.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        getPrefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onPause() {
        super.onPause()
        getPrefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun updateVolumePreview(selected: Int) {
        when (selected) {
            0 -> setVolumeDrawable(
                ringerDrawable = R.drawable.volume_default,
                progressDrawable = R.drawable.volume_default,
                ringerInverse = false,
                progressInverse = false
            )

            1 -> setVolumeDrawable(
                ringerDrawable = R.drawable.volume_gradient,
                progressDrawable = R.drawable.volume_gradient,
                ringerInverse = false,
                progressInverse = false
            )

            2 -> setVolumeDrawable(
                ringerDrawable = R.drawable.volume_double_layer,
                progressDrawable = R.drawable.volume_double_layer,
                ringerInverse = false,
                progressInverse = false
            )

            3 -> setVolumeDrawable(
                ringerDrawable = R.drawable.volume_shaded_layer,
                progressDrawable = R.drawable.volume_shaded_layer,
                ringerInverse = false,
                progressInverse = false
            )

            4 -> setVolumeDrawable(
                ringerDrawable = R.drawable.volume_neumorph,
                progressDrawable = R.drawable.volume_neumorph,
                ringerInverse = false,
                progressInverse = false
            )

            5 -> setVolumeDrawable(
                ringerDrawable = R.drawable.volume_outline_ringer,
                progressDrawable = R.drawable.volume_outline,
                ringerInverse = true,
                progressInverse = true
            )

            6 -> setVolumeDrawable(
                ringerDrawable = R.drawable.volume_neumorph_outline_ringer,
                progressDrawable = R.drawable.volume_neumorph_outline,
                ringerInverse = true,
                progressInverse = true
            )
        }
    }

    private fun setVolumeDrawable(
        ringerDrawable: Int,
        progressDrawable: Int,
        ringerInverse: Boolean,
        progressInverse: Boolean
    ) {
        binding.volumeThickBg.volumeRingerBg.background = ContextCompat.getDrawable(
            appContext,
            ringerDrawable
        )
        binding.volumeThickBg.volumeProgressDrawable.background = ContextCompat.getDrawable(
            appContext,
            progressDrawable
        )

        if (ringerInverse) {
            binding.volumeThickBg.volumeRingerIcon.setBackgroundTintList(
                ContextCompat.getColorStateList(
                    appContext, R.color.textColorPrimary
                )
            )
        } else {
            binding.volumeThickBg.volumeRingerIcon.setBackgroundTintList(
                ContextCompat.getColorStateList(
                    appContext, R.color.textColorPrimaryInverse
                )
            )
        }

        if (progressInverse) {
            binding.volumeThickBg.volumeProgressIcon.setBackgroundTintList(
                ContextCompat.getColorStateList(
                    appContext, R.color.textColorPrimary
                )
            )
        } else {
            binding.volumeThickBg.volumeProgressIcon.setBackgroundTintList(
                ContextCompat.getColorStateList(
                    appContext, R.color.textColorPrimaryInverse
                )
            )
        }
    }

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        for (searchableFragment in searchableFragments) {
            if (searchableFragment.xml == result.resourceFile) {
                replaceFragment(parentFragmentManager, searchableFragment.fragment)
                scrollToPreference()
                SearchPreferenceResult.highlight(volumeStyleFragment, result.key);
                break
            }
        }
    }

    fun scrollToPreference() {
        Handler(Looper.getMainLooper()).postDelayed({
            binding.scrollView.smoothScrollTo(0, binding.fragmentContainer.top)
        }, 180)
    }

    companion object {
        private val volumeStyleFragment = VolumePanel()

        fun getPreferenceFragment(): ControlledPreferenceFragmentCompat {
            return volumeStyleFragment
        }
    }
}