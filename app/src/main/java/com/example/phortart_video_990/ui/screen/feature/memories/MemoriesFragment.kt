package com.example.phortart_video_990.ui.screen.feature.memories

import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.databinding.FragmentMemoriesBinding

class MemoriesFragment : BaseFragment<FragmentMemoriesBinding>(FragmentMemoriesBinding::inflate) {

    override fun initView() {
        binding.tvFunctionName.text = getString(R.string.func_01_memories)
        binding.tvTopTitle.text = getString(R.string.func_01_memories)
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}
