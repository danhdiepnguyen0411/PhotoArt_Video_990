package com.example.phortart_video_990.ui.screen.language

import android.view.View
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

        // Show back button if user already completed intro (opened from settings)
        if (prefs.hasCompletedIntro) {
            binding.btnBack.visibility = View.VISIBLE
            binding.btnConfirm.text = getString(R.string.btn_save_changes)
        } else {
            binding.btnBack.visibility = View.GONE
            binding.btnConfirm.text = getString(R.string.btn_confirm)
        }

        val languages = listOf(
            LanguageModel("vi", "Tiếng Việt", "Vietnamese", "🇻🇳", savedCode == "vi"),
            LanguageModel("en", "English", "Tiếng Anh", "🇺🇸", savedCode == "en"),
            LanguageModel("ja", "日本語", "Tiếng Nhật", "🇯🇵", savedCode == "ja"),
            LanguageModel("ko", "한국어", "Tiếng Hàn", "🇰🇷", savedCode == "ko"),
            LanguageModel("zh", "中文", "Tiếng Trung", "🇨🇳", savedCode == "zh"),
            LanguageModel("fr", "Français", "Tiếng Pháp", "🇫🇷", savedCode == "fr"),
            LanguageModel("es", "Español", "Tiếng Tây Ban Nha", "🇪🇸", savedCode == "es"),
            LanguageModel("de", "Deutsch", "Tiếng Đức", "🇩🇪", savedCode == "de"),
            LanguageModel("it", "Italiano", "Tiếng Ý", "🇮🇹", savedCode == "it"),
            LanguageModel("ar", "العربية", "Tiếng Ả Rập", "🇸🇦", savedCode == "ar"),
            LanguageModel("pt", "Português", "Tiếng Bồ Đào Nha", "🇵🇹", savedCode == "pt"),
            LanguageModel("ru", "Русский", "Tiếng Nga", "🇷🇺", savedCode == "ru")
        )

        adapter = LanguageAdapter(languages) { _ -> }
        binding.rvLanguages.adapter = adapter
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

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
