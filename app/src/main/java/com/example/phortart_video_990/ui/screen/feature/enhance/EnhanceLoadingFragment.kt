package com.example.phortart_video_990.ui.screen.feature.enhance

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.dialog.AppDialogHelper
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.core.utils.safePopBackStack
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.AIPromptRepository
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentEnhanceLoadingBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EnhanceLoadingFragment : BaseFragment<FragmentEnhanceLoadingBinding>(FragmentEnhanceLoadingBinding::inflate) {

    private val promptRepository by lazy { AIPromptRepository(requireContext().applicationContext) }
    private val historyRepository by lazy { HistoryRepository(requireContext().applicationContext) }

    private var progressAnimator: ValueAnimator? = null
    private var finalAnimator: ValueAnimator? = null
    private var crawlJob: Job? = null
    private var sdkJob: Job? = null

    private var userPrompt: String = ""
    private var fullPrompt: String = ""
    private var styleName: String = ""

    private var currentProgress: Float = 0f
    private var isProcessFinished: Boolean = false
    private var isGenerating: Boolean = false

    private var pendingNavigateBundle: Bundle? = null
    private var pendingErrorAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPrompt = arguments?.getString(KEY_USER_PROMPT).orEmpty()
        fullPrompt = arguments?.getString(KEY_FULL_PROMPT).orEmpty()
        styleName = arguments?.getString(KEY_STYLE_NAME).orEmpty()
    }

    override fun initView() {
        updateProgressUi(currentProgress)

        if (!isGenerating && !isProcessFinished) {
            startProgressSimulation()
            startSdkGeneration()
        }
    }

    override fun initListener() {
        binding.header.setOnBackClickListener {
            cancelProcess()
            if (isAdded) {
                findNavController().safePopBackStack()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // 1. Nếu kết quả đã xử lý xong khi app đang ở background, lập tức chuyển màn hình an toàn
        pendingNavigateBundle?.let { bundle ->
            pendingNavigateBundle = null
            navigateToResult(bundle)
            return
        }

        // 2. Nếu có lỗi xảy ra khi app đang ở background, hiển thị dialog lỗi an toàn
        pendingErrorAction?.let { errorAction ->
            pendingErrorAction = null
            errorAction.invoke()
            return
        }

        // 3. Nếu đang trong tiến trình xử lý, đảm bảo UI tiếp tục chạy và không bao giờ bị đứng
        if (!isProcessFinished) {
            updateProgressUi(currentProgress)
            if (progressAnimator?.isRunning != true && crawlJob?.isActive != true) {
                if (currentProgress < 80f) {
                    startProgressSimulation()
                } else {
                    startSlowProgressCrawl()
                }
            }
        }
    }

    private fun startProgressSimulation() {
        progressAnimator?.cancel()
        crawlJob?.cancel()

        val startP = currentProgress
        val targetP1 = 80f
        progressAnimator = ValueAnimator.ofFloat(startP, targetP1).apply {
            duration = 3200
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                updateProgressUi(value)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isProcessFinished) {
                        startSlowProgressCrawl()
                    }
                }
            })
            start()
        }
    }

    private fun startSlowProgressCrawl() {
        crawlJob?.cancel()
        crawlJob = viewLifecycleOwner.lifecycleScope.launch {
            while (!isProcessFinished && currentProgress < 96f) {
                delay(650)
                if (isProcessFinished) break
                val nextP = (currentProgress + 1f).coerceAtMost(96f)
                updateProgressUi(nextP)
            }
        }
    }

    private fun updateProgressUi(value: Float) {
        currentProgress = value
        if (isAdded && bindingOrNull != null) {
            bindingOrNull?.progressCircle?.setProgress(value)
            bindingOrNull?.tvProgressPercent?.text = "${value.toInt()}%"
        }
    }

    private fun startSdkGeneration() {
        if (isGenerating) return
        isGenerating = true

        sdkJob?.cancel()
        sdkJob = lifecycleScope.launch {
            val result = promptRepository.generateImageWithPrompt(fullPrompt)

            if (result.isFailure) {
                handleError(result.exceptionOrNull())
                return@launch
            }

            val outputUrl = result.getOrNull()?.outputUrl.orEmpty()

            // Preload Coil cache và lưu lịch sử
            if (outputUrl.isNotBlank()) {
                val ctx = context?.applicationContext
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

                    try {
                        val dateFormat = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
                        val currentDate = dateFormat.format(Date())
                        val historyItem = HistoryItemModel(
                            type = "Prompt AI",
                            title = userPrompt.ifBlank { getString(R.string.func_03_enhance) },
                            date = currentDate,
                            imageUri = outputUrl,
                            imageUrl = outputUrl,
                            styleName = styleName
                        )
                        historyRepository.addHistoryItem(historyItem)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            finishProgressAndNavigate(outputUrl)
        }
    }

    private fun finishProgressAndNavigate(outputUrl: String) {
        if (isProcessFinished) return
        isProcessFinished = true

        progressAnimator?.cancel()
        crawlJob?.cancel()

        val startVal = currentProgress
        finalAnimator = ValueAnimator.ofFloat(startVal, 100f).apply {
            duration = 350
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                updateProgressUi(v)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    updateProgressUi(100f)
                }
            })
            start()
        }

        val bundle = bundleOf(
            EnhanceResultFragment.KEY_USER_PROMPT to userPrompt,
            EnhanceResultFragment.KEY_IMAGE_URL to outputUrl
        )

        lifecycleScope.launch {
            delay(400)
            if (isResumed && isAdded) {
                navigateToResult(bundle)
            } else {
                pendingNavigateBundle = bundle
            }
        }
    }

    private fun navigateToResult(bundle: Bundle) {
        if (!isAdded) return
        findNavController().navigateSafe(
            R.id.action_enhanceLoadingFragment_to_enhanceResultFragment,
            bundle
        )
    }

    private fun handleError(throwable: Throwable?) {
        progressAnimator?.cancel()
        crawlJob?.cancel()
        finalAnimator?.cancel()
        isGenerating = false

        val errorAction = {
            if (isAdded) {
                val act = activity
                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    AppDialogHelper.showAiErrorDialog(
                        activity = act,
                        onRetry = {
                            if (isAdded) {
                                isProcessFinished = false
                                currentProgress = 0f
                                updateProgressUi(0f)
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
                }
            }
        }

        if (isResumed && isAdded) {
            errorAction.invoke()
        } else {
            pendingErrorAction = errorAction
        }
    }

    private fun cancelProcess() {
        isGenerating = false
        isProcessFinished = true
        sdkJob?.cancel()
        progressAnimator?.cancel()
        finalAnimator?.cancel()
        crawlJob?.cancel()
    }

    override fun onDestroyView() {
        progressAnimator?.cancel()
        finalAnimator?.cancel()
        crawlJob?.cancel()
        super.onDestroyView()
    }

    override fun onDestroy() {
        sdkJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val KEY_USER_PROMPT = "key_user_prompt"
        const val KEY_FULL_PROMPT = "key_full_prompt"
        const val KEY_STYLE_NAME = "key_style_name"
    }
}
