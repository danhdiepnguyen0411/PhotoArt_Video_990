package com.example.phortart_video_990.ui.screen.feature.memories

import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.databinding.FragmentMemoriesBinding

class MemoriesFragment : BaseFragment<FragmentMemoriesBinding>(FragmentMemoriesBinding::inflate) {

    override fun initView() {
        binding.tvTopTitle.text = getString(R.string.feature_video_title).replace("\n", " ")
        binding.tvFunctionName.text = getString(R.string.feature_video_title).replace("\n", " ")
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnCreateVideo.setOnClickListener {
            Toast.makeText(requireContext(), "Đang tạo video bằng công nghệ AI...", Toast.LENGTH_SHORT).show()
        }
    }
}
