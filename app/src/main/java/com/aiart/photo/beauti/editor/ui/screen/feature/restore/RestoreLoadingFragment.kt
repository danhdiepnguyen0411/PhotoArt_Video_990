package com.aiart.photo.beauti.editor.ui.screen.feature.restore

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.net.Uri
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.base.BaseFragment
import com.aiart.photo.beauti.editor.core.dialog.AppDialogHelper
import com.aiart.photo.beauti.editor.core.utils.navigateSafe
import com.aiart.photo.beauti.editor.core.utils.safePopBackStack
import com.aiart.photo.beauti.editor.data.model.HistoryItemModel
import com.aiart.photo.beauti.editor.data.repository.HistoryRepository
import com.aiart.photo.beauti.editor.data.repository.RestoreRepository
import com.aiart.photo.beauti.editor.databinding.FragmentRestoreLoadingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.aiart.photo.beauti.editor.core.utils.ErrorFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RestoreLoadingFragment : BaseFragment<FragmentRestoreLoadingBinding>(FragmentRestoreLoadingBinding::inflate) {

    private val restoreRepository by lazy { RestoreRepository(requireContext().applicationContext) }
    private val historyRepository by lazy { HistoryRepository(requireContext().applicationContext) }

    private var progressAnimator: ValueAnimator? = null
    private var finalAnimator: ValueAnimator? = null
    private var crawlJob: Job? = null
    private var sdkJob: Job? = null

    private var originalUriString: String? = null
    private var currentProgress: Float = 0f
    private var isProcessFinished: Boolean = false
    private var isRestoring: Boolean = false

    private var pendingNavigateBundle: Bundle? = null
    private var pendingErrorAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        originalUriString = arguments?.getString(KEY_IMAGE_URI)
    }

    override fun initView() {
        updateProgressUi(currentProgress)

        if (!isRestoring && !isProcessFinished) {
            startProgressSimulation()
            startSdkRestoreProcess()
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

    private fun startSdkRestoreProcess() {
        if (isRestoring) return

        val uriStr = originalUriString
        if (uriStr.isNullOrBlank()) {
            if (isAdded && context != null) {
                Toast.makeText(requireContext(), getString(R.string.toast_image_not_found), Toast.LENGTH_SHORT).show()
            }
            if (isAdded) findNavController().safePopBackStack()
            return
        }

        val uri = Uri.parse(uriStr)
        isRestoring = true

        sdkJob?.cancel()
        sdkJob = viewLifecycleOwner.lifecycleScope.launch {
            // 1. Chuẩn bị và nén ảnh hoàn toàn trên Dispatchers.IO để không bao giờ làm đơ UI
            val file = withContext(Dispatchers.IO) {
                restoreRepository.createTempFileFromUri(uri)
            }
            if (file == null) {
                isRestoring = false
                if (isAdded && context != null) {
                    Toast.makeText(requireContext(), getString(R.string.toast_cannot_read_image), Toast.LENGTH_SHORT).show()
                }
                if (isAdded) findNavController().safePopBackStack()
                return@launch
            }

            // 2. Gọi backend SDK phục hồi ảnh
            val result = restoreRepository.restoreImage(file)

            if (result.isFailure) {
                handleError(result.exceptionOrNull())
                return@launch
            }

            val restoreData = result.getOrNull()
            val finalInputUrl = restoreData?.inputUrl?.takeIf { it.isNotBlank() } ?: file.absolutePath
            val finalOutputUrl = restoreData?.outputUrl?.takeIf { it.isNotBlank() } ?: file.absolutePath

            // 3. Preload, căn chỉnh góc xoay và lưu lịch sử hoàn toàn trên Dispatchers.IO
            val processedPair = withContext(Dispatchers.IO) {
                processPostRestore(file, finalInputUrl, finalOutputUrl)
            }

            finishProgressAndNavigate(processedPair.first, processedPair.second)
        }
    }

    private suspend fun processPostRestore(
        inputFile: File,
        inputUrl: String,
        outputUrl: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val ctx = context?.applicationContext ?: return@withContext Pair(inputUrl, outputUrl)
        var fixedOutputUrl = outputUrl

        try {
            val imageLoader = coil.Coil.imageLoader(ctx)
            val outRequest = coil.request.ImageRequest.Builder(ctx)
                .data(outputUrl)
                .allowHardware(false)
                .build()
            val outResult = imageLoader.execute(outRequest)

            if (outResult is coil.request.SuccessResult) {
                val outW = outResult.drawable.intrinsicWidth
                val outH = outResult.drawable.intrinsicHeight

                if (outW > 0 && outH > 0) {
                    val inputBounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(inputFile.absolutePath, inputBounds)
                    val inRot = com.aiart.photo.beauti.editor.core.utils.ImageCompressor.getExifRotation(inputFile)
                    val actualInW = if (inRot == 90 || inRot == 270) inputBounds.outHeight else inputBounds.outWidth
                    val actualInH = if (inRot == 90 || inRot == 270) inputBounds.outWidth else inputBounds.outHeight

                    val isInputPortrait = actualInH > actualInW
                    val isOutputPortrait = outH > outW

                    if (isInputPortrait != isOutputPortrait && inputBounds.outWidth > 0 && inputBounds.outHeight > 0) {
                        var outBitmap: android.graphics.Bitmap? = null
                        var rotatedBmp: android.graphics.Bitmap? = null
                        try {
                            outBitmap = com.aiart.photo.beauti.editor.core.utils.MediaSaveUtils.drawableToBitmap(outResult.drawable)
                            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
                            rotatedBmp = android.graphics.Bitmap.createBitmap(outBitmap, 0, 0, outBitmap.width, outBitmap.height, matrix, true)
                            val fixedFile = File(ctx.cacheDir, "restore_aligned_${System.currentTimeMillis()}.jpg")
                            java.io.FileOutputStream(fixedFile).use { fos ->
                                rotatedBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, fos)
                            }
                            fixedOutputUrl = fixedFile.absolutePath
                        } catch (t: Throwable) {
                            android.util.Log.e("RestoreLoading", "Không thể xoay lại ảnh output: ${t.message}")
                        } finally {
                            try {
                                if (rotatedBmp != null && rotatedBmp != outBitmap && !rotatedBmp.isRecycled) {
                                    rotatedBmp.recycle()
                                }
                                if (outBitmap != null && !outBitmap.isRecycled) {
                                    outBitmap.recycle()
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }

            if (inputUrl.startsWith("http://") || inputUrl.startsWith("https://")) {
                val inRequest = coil.request.ImageRequest.Builder(ctx)
                    .data(inputUrl)
                    .build()
                imageLoader.execute(inRequest)
            }
        } catch (t: Throwable) {
            android.util.Log.e("RestoreLoading", "Lỗi trong processPostRestore: ${t.message}")
        }

        try {
            val currentTime = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
            val currentDate = dateFormat.format(Date(currentTime))
            val historyItem = HistoryItemModel(
                type = "Khôi phục",
                title = getString(R.string.func_02_restore),
                date = currentDate,
                imageUri = fixedOutputUrl,
                imageUrl = fixedOutputUrl,
                beforeUri = inputUrl,
                timestamp = currentTime
            )
            historyRepository.addHistoryItem(historyItem)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Pair(inputUrl, fixedOutputUrl)
    }

    private fun finishProgressAndNavigate(finalInputUrl: String, finalOutputUrl: String) {
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
            RestoreResultFragment.KEY_ORIGINAL_URI to finalInputUrl,
            RestoreResultFragment.KEY_RESTORED_URL to finalOutputUrl
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
            R.id.action_restoreLoadingFragment_to_restoreResultFragment,
            bundle
        )
    }

    private fun handleError(throwable: Throwable?) {
        progressAnimator?.cancel()
        crawlJob?.cancel()
        finalAnimator?.cancel()
        isRestoring = false

        val errorAction = {
            if (isAdded) {
                val act = activity
                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    val formattedMsg = context?.let { ErrorFormatter.formatVideoErrorMessage(it, throwable) }
                    AppDialogHelper.showRestoreErrorDialog(
                        activity = act,
                        subtitle = formattedMsg,
                        onRetry = {
                            if (isAdded) {
                                isProcessFinished = false
                                currentProgress = 0f
                                updateProgressUi(0f)
                                startProgressSimulation()
                                startSdkRestoreProcess()
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
        isRestoring = false
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
        const val KEY_IMAGE_URI = "key_image_uri"
    }
}
