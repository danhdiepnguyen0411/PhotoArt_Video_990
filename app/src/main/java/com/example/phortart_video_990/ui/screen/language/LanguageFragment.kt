package com.example.phortart_video_990.ui.screen.language

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.Resources
import android.os.Build
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.LocaleHelper
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.data.model.LanguageModel
import com.example.phortart_video_990.databinding.FragmentLanguageBinding

class LanguageFragment : BaseFragment<FragmentLanguageBinding>(FragmentLanguageBinding::inflate) {

    private lateinit var adapter: LanguageAdapter
    private var pulseAnimatorSet: AnimatorSet? = null
    private var isHandAtCheckmark = false
    private var isTutorialActive = false
    private val targetSuggestedPosition = 3 // Position 4 (0-based index 3)
    private var initialLangCode: String = "vi"
    private var previewLangCode: String = "vi"

    override fun initView() {
        binding.rvLanguages.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLanguages.itemAnimator = null

        val prefs = PreferencesManager.getInstance(requireContext())
        val isFromSettings = prefs.hasCompletedIntro
        val savedCode = prefs.languageCode
        initialLangCode = savedCode ?: "vi"
        previewLangCode = initialLangCode

        isTutorialActive = !isFromSettings
        isHandAtCheckmark = false

        if (isFromSettings) {
            binding.btnBack.visibility = View.VISIBLE
            binding.btnConfirm.text = getString(R.string.btn_save_changes)
            binding.ivHandGif.visibility = View.GONE
        } else {
            binding.btnBack.visibility = View.GONE
            binding.btnConfirm.text = getString(R.string.btn_confirm)
        }

        // Base language list
        val baseList = mutableListOf(
            LanguageModel("vi", "Tiếng Việt", getString(R.string.lang_sub_vi), "flags/vn.svg", subResId = R.string.lang_sub_vi),
            LanguageModel("en", "English", getString(R.string.lang_sub_en), "flags/en.svg", subResId = R.string.lang_sub_en),
            LanguageModel("ja", "日本語", getString(R.string.lang_sub_ja), "flags/jp.svg", subResId = R.string.lang_sub_ja),
            LanguageModel("ko", "한국어", getString(R.string.lang_sub_ko), "flags/kr.svg", subResId = R.string.lang_sub_ko),
            LanguageModel("zh", "中文", getString(R.string.lang_sub_zh), "flags/cn.svg", subResId = R.string.lang_sub_zh),
            LanguageModel("fr", "Français", getString(R.string.lang_sub_fr), "flags/fr.svg", subResId = R.string.lang_sub_fr),
            LanguageModel("es", "Español", getString(R.string.lang_sub_es), "flags/es.svg", subResId = R.string.lang_sub_es),
            LanguageModel("de", "Deutsch", getString(R.string.lang_sub_de), "flags/de.svg", subResId = R.string.lang_sub_de),
            LanguageModel("it", "Italiano", getString(R.string.lang_sub_it), "flags/it.svg", subResId = R.string.lang_sub_it),
            LanguageModel("ar", "العربية", getString(R.string.lang_sub_ar), "flags/sa.svg", subResId = R.string.lang_sub_ar),
            LanguageModel("pt", "Português", getString(R.string.lang_sub_pt), "flags/pt.svg", subResId = R.string.lang_sub_pt),
            LanguageModel("ru", "Русский", getString(R.string.lang_sub_ru), "flags/ru.svg", subResId = R.string.lang_sub_ru),
            LanguageModel("hi", "हिन्दी", getString(R.string.lang_sub_hi), "flags/ind.svg", subResId = R.string.lang_sub_hi),
            LanguageModel("id", "Bahasa Indonesia", getString(R.string.lang_sub_id), "flags/id.svg", subResId = R.string.lang_sub_id)
        )

        // Fetch real device system language from Resources.getSystem() (unaffected by app locale overrides)
        val sysLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale
        }

        val sysLang = sysLocale.language.lowercase()
        val sysTag = sysLocale.toLanguageTag().lowercase()

        // Place device system language at position 4 (index 3)
        var sysIndex = baseList.indexOfFirst { it.code.lowercase() == sysTag || it.code.lowercase() == sysLang }
        if (sysIndex == -1) {
            sysIndex = baseList.indexOfFirst { it.code.lowercase().startsWith(sysLang) || sysLang.startsWith(it.code.lowercase()) }
        }

        if (sysIndex != -1) {
            if (sysIndex != targetSuggestedPosition) {
                val sysItem = baseList.removeAt(sysIndex)
                baseList.add(targetSuggestedPosition, sysItem)
            }
        } else {
            val enIndex = baseList.indexOfFirst { it.code.lowercase().startsWith("en") }
            if (enIndex != -1 && enIndex != targetSuggestedPosition) {
                val enItem = baseList.removeAt(enIndex)
                baseList.add(targetSuggestedPosition, enItem)
            }
        }

        if (isFromSettings && !savedCode.isNullOrEmpty()) {
            baseList.forEach { it.isSelected = (it.code == savedCode) }
        } else {
            // In onboarding, not selected yet until user taps or taps tutorial hand
            baseList.forEach { it.isSelected = false }
        }

        adapter = LanguageAdapter { selectedItem ->
            applyPreviewLanguage(selectedItem.code)
            binding.btnConfirm.isEnabled = true
            binding.btnConfirm.alpha = 1f
            if (isTutorialActive && !isHandAtCheckmark) {
                moveHandFromSuggestedToCheckmark()
            }
        }
        binding.rvLanguages.adapter = adapter
        adapter.submitList(baseList)

        if (isFromSettings && !savedCode.isNullOrEmpty()) {
            applyPreviewLanguage(savedCode)
        }

        val hasSelection = baseList.any { it.isSelected }
        binding.btnConfirm.isEnabled = hasSelection
        binding.btnConfirm.alpha = if (hasSelection) 1f else 0.5f

        binding.rvLanguages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (isTutorialActive && !isHandAtCheckmark) {
                    updateHandPositionAtSuggestedLanguage()
                }
            }
        })

        if (isTutorialActive) {
            binding.rvLanguages.post {
                setupHandAtSuggestedLanguage()
            }
        } else {
            binding.ivHandGif.visibility = View.GONE
        }
    }

    private fun setupHandAtSuggestedLanguage() {
        if (!isTutorialActive || isHandAtCheckmark || context == null) {
            binding.ivHandGif.visibility = View.GONE
            return
        }

        val ctx = requireContext()
        val imageLoader = ImageLoader.Builder(ctx)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()

        binding.ivHandGif.load(R.drawable.click, imageLoader)
        binding.ivHandGif.visibility = View.VISIBLE

        binding.rvLanguages.post {
            if (!isTutorialActive || isHandAtCheckmark) return@post
            updateHandPositionAtSuggestedLanguage()

            binding.ivHandGif.setOnClickListener {
                val suggestedLang = adapter.getLanguageAt(targetSuggestedPosition)
                if (suggestedLang != null) {
                    adapter.selectLanguage(suggestedLang.code)
                    applyPreviewLanguage(suggestedLang.code)
                    binding.btnConfirm.isEnabled = true
                    binding.btnConfirm.alpha = 1f
                }
                moveHandFromSuggestedToCheckmark()
            }

            startPulseOnView(binding.ivHandGif)
        }
    }

    private fun updateHandPositionAtSuggestedLanguage() {
        if (!isTutorialActive || isHandAtCheckmark || view == null) return

        val viewHolder = binding.rvLanguages.findViewHolderForAdapterPosition(targetSuggestedPosition)
        val targetItemView = viewHolder?.itemView

        if (targetItemView != null) {
            val radioView = targetItemView.findViewById<View>(R.id.ivRadio) ?: targetItemView
            val radioLoc = IntArray(2)
            radioView.getLocationOnScreen(radioLoc)

            val rootLoc = IntArray(2)
            binding.rootView.getLocationOnScreen(rootLoc)

            val isVisible = radioLoc[1] > rootLoc[1] && (radioLoc[1] + radioView.height) < (rootLoc[1] + binding.rootView.height)
            if (isVisible) {
                val density = resources.displayMetrics.density
                val startX = (radioLoc[0] - rootLoc[0] + radioView.width / 2f - binding.ivHandGif.width / 2f + 2 * density)
                val startY = (radioLoc[1] - rootLoc[1] + radioView.height / 2f - binding.ivHandGif.height / 2f + 6 * density)

                binding.ivHandGif.x = startX
                binding.ivHandGif.y = startY
                binding.ivHandGif.rotation = 0f
                binding.ivHandGif.visibility = View.VISIBLE
            } else {
                binding.ivHandGif.visibility = View.GONE
            }
        } else {
            binding.ivHandGif.visibility = View.GONE
        }
    }

    private fun moveHandFromSuggestedToCheckmark() {
        if (!isTutorialActive || isHandAtCheckmark || view == null) return
        isHandAtCheckmark = true
        pulseAnimatorSet?.cancel()

        binding.btnConfirm.isEnabled = true
        binding.btnConfirm.alpha = 1f

        binding.ivHandGif.setOnClickListener {
            binding.btnConfirm.performClick()
        }

        binding.rootView.post {
            updateHandPositionAtConfirmButton(animate = true)
        }
    }

    private fun updateHandPositionAtConfirmButton(animate: Boolean = false) {
        if (!isTutorialActive || !isHandAtCheckmark || view == null) return
        val btnLoc = IntArray(2)
        binding.btnConfirm.getLocationOnScreen(btnLoc)

        val rootLoc = IntArray(2)
        binding.rootView.getLocationOnScreen(rootLoc)

        val density = resources.displayMetrics.density
        val endX = (btnLoc[0] - rootLoc[0] + binding.btnConfirm.width / 2f - binding.ivHandGif.width / 2f + 12 * density)
        val endY = (btnLoc[1] - rootLoc[1] + binding.btnConfirm.height / 2f - binding.ivHandGif.height / 2f + 4 * density)

        if (animate) {
            binding.ivHandGif.animate()
                .x(endX)
                .y(endY)
                .rotation(15f)
                .setDuration(600)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    startPulseOnView(binding.ivHandGif)
                }
                .start()
        } else {
            binding.ivHandGif.x = endX
            binding.ivHandGif.y = endY
            binding.ivHandGif.rotation = 15f
        }
    }

    private fun startPulseOnView(targetView: View) {
        pulseAnimatorSet?.cancel()
        val scaleX = ObjectAnimator.ofFloat(targetView, "scaleX", 1f, 1.2f, 1f).apply {
            duration = 750
            repeatCount = ValueAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(targetView, "scaleY", 1f, 1.2f, 1f).apply {
            duration = 750
            repeatCount = ValueAnimator.INFINITE
        }

        pulseAnimatorSet = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            start()
        }
    }

    private fun hideHandTutorial() {
        pulseAnimatorSet?.cancel()
        pulseAnimatorSet = null
        binding.ivHandGif.animate().cancel()
        binding.ivHandGif.visibility = View.GONE
    }

    private fun applyPreviewLanguage(code: String) {
        previewLangCode = code
        val act = activity ?: return
        LocaleHelper.setLocale(act, code)

        val isRtl = code.lowercase() == "ar"
        val dir = if (isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

        binding.rootView.layoutDirection = dir
        binding.llHeader.layoutDirection = dir
        binding.clNavHeader.layoutDirection = dir
        binding.rvLanguages.layoutDirection = dir
        act.window.decorView.layoutDirection = dir

        binding.btnBack.scaleX = if (isRtl) -1f else 1f
        binding.tvLanguageTitle.text = getString(R.string.language_title)
        binding.tvLanguageDesc.text = getString(R.string.language_choose_desc)

        val prefs = PreferencesManager.getInstance(act)
        val isFromSettings = prefs.hasCompletedIntro
        binding.btnConfirm.text = if (isFromSettings) {
            getString(R.string.btn_save_changes)
        } else {
            getString(R.string.btn_confirm)
        }

        adapter.updateSubNames(act, isRtl)

        if (isTutorialActive && isHandAtCheckmark) {
            binding.rootView.post {
                binding.rootView.post {
                    updateHandPositionAtConfirmButton(animate = false)
                }
            }
        }
    }

    private fun restoreInitialLanguageAndExit() {
        hideHandTutorial()
        if (previewLangCode != initialLangCode) {
            val act = activity
            if (act != null) {
                LocaleHelper.setLocale(act, initialLangCode)
                val isRtl = initialLangCode.lowercase() == "ar"
                val dir = if (isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                act.window.decorView.layoutDirection = dir
            }
        }
        findNavController().popBackStack()
    }

    override fun initListener() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                restoreInitialLanguageAndExit()
            }
        })

        binding.btnBack.setOnClickListener {
            restoreInitialLanguageAndExit()
        }

        binding.btnConfirm.setOnClickListener {
            if (!binding.btnConfirm.isEnabled) return@setOnClickListener
            val selected = adapter.getSelectedItem() ?: return@setOnClickListener
            hideHandTutorial()
            val langCode = selected.code
            val langName = selected.name

            val prefs = PreferencesManager.getInstance(requireContext())
            // Save ONLY when user explicitly presses the confirm / save button!
            prefs.saveLanguage(langCode, langName)
            LocaleHelper.setLocale(requireActivity(), langCode)

            val isRtl = langCode.lowercase() == "ar"
            requireActivity().window.decorView.layoutDirection = if (isRtl) {
                View.LAYOUT_DIRECTION_RTL
            } else {
                View.LAYOUT_DIRECTION_LTR
            }

            if (!prefs.hasCompletedIntro) {
                findNavController().navigate(R.id.action_languageFragment_to_introFragment)
            } else {
                findNavController().navigate(R.id.action_languageFragment_to_mainFragment)
            }
        }
    }

    override fun onDestroyView() {
        hideHandTutorial()
        super.onDestroyView()
    }
}
