package com.example.phortart_video_990.ui.screen.feature.enhance

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.databinding.FragmentEnhanceBinding

class EnhanceFragment : BaseFragment<FragmentEnhanceBinding>(FragmentEnhanceBinding::inflate) {

    private var selectedStyleName: String = "Cyber Move"

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            binding.topBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarInset
            }
            binding.btnGeneratePrompt.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarInset + (24 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        binding.etPromptInput.doAfterTextChanged { text ->
            val count = text?.length ?: 0
            binding.tvPromptCounter.text = "$count/500"
        }

        updateSelectedStyle(1)
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnFavorite.setOnClickListener {
            Toast.makeText(requireContext(), "Đã lưu vào danh sách yêu thích", Toast.LENGTH_SHORT).show()
        }

        // Suggestion chips
        binding.chipAnimeGirl.setOnClickListener {
            binding.etPromptInput.setText("Anime girl xinh đẹp, phong cách truyện tranh Nhật Bản")
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        binding.chipFantasy.setOnClickListener {
            binding.etPromptInput.setText("Lâu đài kỳ ảo bay giữa bầu trời thần tiên rực rỡ")
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        binding.chipCutePet.setOnClickListener {
            binding.etPromptInput.setText("Mèo con dễ thương đội mũ len ngồi trong tách trà")
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        binding.chipCyberpunk.setOnClickListener {
            binding.etPromptInput.setText("Chiến binh Cyberpunk neon rực sáng đêm mưa hiện đại")
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        binding.chipNature.setOnClickListener {
            binding.etPromptInput.setText("Rừng cây phát sáng bên hồ nước trong vắt thơ mộng")
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        binding.chipPortrait.setOnClickListener {
            binding.etPromptInput.setText("Chân dung thiếu nữ thanh tú với ánh sáng hoàng hôn dịu nhẹ")
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        // Style Selection
        binding.cardStyle1.setOnClickListener { updateSelectedStyle(1) }
        binding.cardStyle2.setOnClickListener { updateSelectedStyle(2) }
        binding.cardStyle3.setOnClickListener { updateSelectedStyle(3) }

        // Start Generate
        binding.btnGeneratePrompt.setOnClickListener {
            val userPrompt = binding.etPromptInput.text?.toString()?.trim().orEmpty()
            if (userPrompt.isBlank()) {
                Toast.makeText(requireContext(), "Vui lòng nhập mô tả ý tưởng ảnh", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Combine user prompt with chosen style
            val fullPrompt = "$userPrompt, theo phong cách $selectedStyleName"

            findNavController().navigate(
                R.id.action_enhanceFragment_to_enhanceLoadingFragment,
                bundleOf(
                    EnhanceLoadingFragment.KEY_USER_PROMPT to userPrompt,
                    EnhanceLoadingFragment.KEY_FULL_PROMPT to fullPrompt,
                    EnhanceLoadingFragment.KEY_STYLE_NAME to selectedStyleName
                )
            )
        }
    }

    private fun updateSelectedStyle(index: Int) {
        val selectedColor = Color.parseColor("#4D70F7")
        val transparentColor = Color.TRANSPARENT

        binding.cardStyle1.strokeColor = if (index == 1) selectedColor else transparentColor
        binding.cardStyle2.strokeColor = if (index == 2) selectedColor else transparentColor
        binding.cardStyle3.strokeColor = if (index == 3) selectedColor else transparentColor

        selectedStyleName = when (index) {
            1 -> "Cyber Move"
            2 -> "Street Vibes"
            3 -> "Urban Renaissance"
            else -> "Cyber Move"
        }
    }
}
