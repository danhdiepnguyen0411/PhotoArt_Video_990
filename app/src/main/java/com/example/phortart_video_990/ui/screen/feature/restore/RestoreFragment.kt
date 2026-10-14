package com.example.phortart_video_990.ui.screen.feature.restore

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Outline
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.core.utils.safePopBackStack
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentRestoreBinding

class RestoreFragment : BaseFragment<FragmentRestoreBinding>(FragmentRestoreBinding::inflate) {

    private val historyRepository by lazy { HistoryRepository(requireContext()) }
    private var floatAnimator: ObjectAnimator? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && isAdded) {
            findNavController().navigateSafe(
                R.id.action_restoreFragment_to_restoreLoadingFragment,
                androidx.core.os.bundleOf(RestoreLoadingFragment.KEY_IMAGE_URI to uri.toString())
            )
        }
    }

    override fun initView() {
        setupWindowInsets()
        setupCircularOutlines()
        startFloatingAnimation()
        startEntranceAnimation()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            binding.btnStartRestore.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarInset + (24 * resources.displayMetrics.density).toInt()
            }
            insets
        }
    }

    /**
     * Enforces strict circular outline providers on all round icon badges
     * to eliminate square shadow clipping artifacts across all Android OS versions.
     */
    private fun setupCircularOutlines() {
        val circularOutlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }

        binding.badgeCompare.apply {
            outlineProvider = circularOutlineProvider
            clipToOutline = true
        }

        binding.layoutFeatureIcon1.apply {
            outlineProvider = circularOutlineProvider
            clipToOutline = true
        }

        binding.layoutFeatureIcon2.apply {
            outlineProvider = circularOutlineProvider
            clipToOutline = true
        }

        binding.layoutFeatureIcon3.apply {
            outlineProvider = circularOutlineProvider
            clipToOutline = true
        }
    }

    /**
     * Continuous floating animation matching the web reference float keyframe (float 4s ease-in-out infinite).
     */
    private fun startFloatingAnimation() {
        val floatDistance = -7f * resources.displayMetrics.density
        floatAnimator = ObjectAnimator.ofFloat(binding.layoutIllustration, View.TRANSLATION_Y, 0f, floatDistance).apply {
            duration = 2000L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Smooth entrance animation (staggered riseIn) for illustration, title, description, and feature rows.
     */
    private fun startEntranceAnimation() {
        val offset = 14f * resources.displayMetrics.density
        val viewsWithDelay = listOf(
            binding.layoutIllustration to 0L,
            binding.tvTitle to 60L,
            binding.tvDescription to 120L,
            binding.itemFeature1 to 180L,
            binding.itemFeature2 to 240L,
            binding.itemFeature3 to 300L,
            binding.btnStartRestore to 360L
        )

        viewsWithDelay.forEach { (view, delay) ->
            view.alpha = 0f
            view.translationY = offset
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(520L)
                .setStartDelay(delay)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    override fun initListener() {
        binding.header.setOnBackClickListener {
            if (isAdded) findNavController().safePopBackStack()
        }

        binding.btnStartRestore.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    override fun onDestroyView() {
        floatAnimator?.cancel()
        floatAnimator = null
        super.onDestroyView()
    }
}
