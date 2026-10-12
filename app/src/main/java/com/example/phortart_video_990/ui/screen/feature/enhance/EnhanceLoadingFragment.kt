package com.example.phortart_video_990.ui.screen.feature.enhance

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.AIPromptRepository
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentEnhanceLoadingBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.core.utils.safePopBackStack

class EnhanceLoadingFragment : BaseFragment<FragmentEnhanceLoadingBinding>(FragmentEnhanceLoadingBinding::inflate) {

    private val promptRepository by lazy { AIPromptRepository(requireContext()) }
    private val historyRepository by lazy { HistoryRepository(requireContext()) }
    private var progressAnimator: ValueAnimator? = null
    private var finalAnimator: ValueAnimator? = null

    private var userPrompt: String = ""
    private var fullPrompt: String = ""
    private var styleName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPrompt = arguments?.getString(KEY_USER_PROMPT).orEmpty()
        fullPrompt = arguments?.getString(KEY_FULL_PROMPT).orEmpty()
        styleName = arguments?.getString(KEY_STYLE_NAME).orEmpty()
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.btnBack.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarInset + (14 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        binding.progressCircle.setProgress(0f)
        binding.tvProgressPercent.text = "0%"

        startProgressSimulation()
        startSdkGeneration()
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            progressAnimator?.cancel()
            finalAnimator?.cancel()
            if (isAdded) {
                findNavController().safePopBackStack()
            }
        }
    }

    private fun startProgressSimulation() {
        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(0f, 92f).apply {
            duration = 4500
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                if (isAdded && bindingOrNull != null) {
                    val value = animator.animatedValue as Float
                    bindingOrNull?.progressCircle?.setProgress(value)
                    bindingOrNull?.tvProgressPercent?.text = "${value.toInt()}%"
                }
            }
            start()
        }
    }

    private fun startSdkGeneration() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = promptRepository.generateImageWithPrompt(fullPrompt)
            if (!isAdded || view == null) return@launch

            if (result.isFailure) {
                progressAnimator?.cancel()
                finalAnimator?.cancel()
                val act = activity ?: return@launch
                com.example.phortart_video_990.core.dialog.AppDialogHelper.showAiErrorDialog(
                    activity = act,
                    onRetry = {
                        if (isAdded) {
                            startProgressSimulation()
                            startSdkGeneration()
                        }
                    },
                    onCancel = {
                        if (isAdded) {
                            findNavController().safePopBackStack()
                        }
                    }
                )
                return@launch
            }
            val outputUrl = result.getOrNull()?.outputUrl.orEmpty()

            // Preload the AI generated image into Coil cache so it renders immediately upon opening result screen
            if (outputUrl.isNotBlank()) {
                val ctx = context
                if (ctx != null) {
                    try {
                        val imageLoader = coil.Coil.imageLoader(ctx)
                        val request = coil.request.ImageRequest.Builder(ctx)
                            .data(outputUrl)
                            .build()
                        imageLoader.execute(request)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Tự động lưu vào lịch sử ngay khi gen ra thành công
                    try {
                        val dateFormat = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
                        val currentDate = dateFormat.format(Date())
                        val historyItem = HistoryItemModel(
                            type = "Prompt AI",
                            title = userPrompt.ifBlank { getString(R.string.func_03_enhance) },
                            date = currentDate,
                            imageUri = outputUrl,
                            imageUrl = outputUrl
                        )
                        historyRepository.addHistoryItem(historyItem)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (!isAdded || bindingOrNull == null) return@launch

            // Animate to 100%
            progressAnimator?.cancel()
            val currentProgress = bindingOrNull?.progressCircle?.getProgress() ?: 90f
            finalAnimator = ValueAnimator.ofFloat(currentProgress, 100f).apply {
                duration = 350
                addUpdateListener { anim ->
                    if (isAdded && bindingOrNull != null) {
                        val v = anim.animatedValue as Float
                        bindingOrNull?.progressCircle?.setProgress(v)
                        bindingOrNull?.tvProgressPercent?.text = "${v.toInt()}%"
                    }
                }
            }
            finalAnimator?.start()

            delay(400)

            if (!isAdded) return@launch

            findNavController().navigateSafe(
                R.id.action_enhanceLoadingFragment_to_enhanceResultFragment,
                bundleOf(
                    EnhanceResultFragment.KEY_USER_PROMPT to userPrompt,
                    EnhanceResultFragment.KEY_IMAGE_URL to outputUrl
                )
            )
        }
    }

    override fun onDestroyView() {
        progressAnimator?.cancel()
        finalAnimator?.cancel()
        super.onDestroyView()
    }

    companion object {
        const val KEY_USER_PROMPT = "key_user_prompt"
        const val KEY_FULL_PROMPT = "key_full_prompt"
        const val KEY_STYLE_NAME = "key_style_name"
    }
}
