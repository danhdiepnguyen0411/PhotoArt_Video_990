package com.example.phortart_video_990.ui.screen.language

import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.LocaleHelper
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.data.model.LanguageModel
import com.example.phortart_video_990.databinding.FragmentLanguageBinding

class LanguageFragment : BaseFragment<FragmentLanguageBinding>(FragmentLanguageBinding::inflate) {

    private lateinit var adapter: LanguageAdapter

    override fun initView() {
        binding.rvLanguages.layoutManager = LinearLayoutManager(requireContext())
        val prefs = PreferencesManager.getInstance(requireContext())
        val savedCode = prefs.languageCode

        val languages = listOf(
            LanguageModel("en", "English", "🇺🇸", savedCode == "en"),
            LanguageModel("vi", "Tiếng Việt", "🇻🇳", savedCode == "vi"),
            LanguageModel("es", "Español", "🇪🇸", savedCode == "es"),
            LanguageModel("fr", "Français", "🇫🇷", savedCode == "fr"),
            LanguageModel("hi", "हिन्दी (Hindi)", "🇮🇳", savedCode == "hi"),
            LanguageModel("ja", "日本語 (Japanese)", "🇯🇵", savedCode == "ja"),
            LanguageModel("ko", "한국어 (Korean)", "🇰🇷", savedCode == "ko")
        )

        adapter = LanguageAdapter(languages) { _ -> }
        binding.rvLanguages.adapter = adapter
    }

    override fun initListener() {
        binding.btnConfirm.setOnClickListener {
            val selected = adapter.getSelectedItem() ?: return@setOnClickListener
            val prefs = PreferencesManager.getInstance(requireContext())
            prefs.saveLanguage(selected.code, selected.name)
            LocaleHelper.setLocale(requireContext(), selected.code)

            if (!prefs.hasCompletedIntro) {
                findNavController().navigate(R.id.action_languageFragment_to_introFragment)
            } else {
                if (!findNavController().popBackStack()) {
                    findNavController().navigate(R.id.action_languageFragment_to_mainFragment)
                }
            }
        }
    }
}
