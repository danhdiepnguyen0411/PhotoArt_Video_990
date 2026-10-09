package com.example.phortart_video_990.ui.screen.feature.enhance

import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.databinding.FragmentEnhanceBinding

class EnhanceFragment : BaseFragment<FragmentEnhanceBinding>(FragmentEnhanceBinding::inflate) {

    override fun initView() {
        binding.tvTopTitle.text = getString(R.string.feature_prompt_title).replace("\n", " ")
        binding.tvFunctionName.text = getString(R.string.feature_prompt_title).replace("\n", " ")

        binding.etPromptInput.doAfterTextChanged { text ->
            val count = text?.length ?: 0
            binding.tvPromptCounter.text = "$count/500"
        }
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.tagCyberpunk.setOnClickListener {
            binding.etPromptInput.setText("Chân dung nghệ thuật chiến binh nữ phong cách Cyberpunk neon rực rỡ")
        }

        binding.tagAnime.setOnClickListener {
            binding.etPromptInput.setText("Phong cảnh thành phố anime Ghibli dưới ánh hoàng hôn thơ mộng")
        }

        binding.tagFantasy.setOnClickListener {
            binding.etPromptInput.setText("Lâu đài thần tiên bay trên mây với thác nước phát sáng 3D cinematic")
        }

        binding.btnGeneratePrompt.setOnClickListener {
            val text = binding.etPromptInput.text?.toString()?.trim()
            if (text.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập ý tưởng tạo ảnh", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Đang sinh ảnh nghệ thuật bằng AI...", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
