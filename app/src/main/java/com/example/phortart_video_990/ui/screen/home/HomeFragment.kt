package com.example.phortart_video_990.ui.screen.home

import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.databinding.FragmentHomeBinding

class HomeFragment : BaseFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {

    override fun initListener() {
        // 01: Gom kỷ niệm
        binding.card01Memories.setOnClickListener {
            parentFragment?.parentFragment?.findNavController()?.navigate(
                R.id.action_mainFragment_to_memoriesFragment
            ) ?: findNavController().navigate(R.id.action_mainFragment_to_memoriesFragment)
        }

        // 02: Khôi phục ảnh
        binding.card02Restore.setOnClickListener {
            parentFragment?.parentFragment?.findNavController()?.navigate(
                R.id.action_mainFragment_to_restoreFragment
            ) ?: findNavController().navigate(R.id.action_mainFragment_to_restoreFragment)
        }

        // 03: Làm nét
        binding.card03Enhance.setOnClickListener {
            parentFragment?.parentFragment?.findNavController()?.navigate(
                R.id.action_mainFragment_to_enhanceFragment
            ) ?: findNavController().navigate(R.id.action_mainFragment_to_enhanceFragment)
        }

        // 04: Ghép nhạc
        binding.card04Music.setOnClickListener {
            parentFragment?.parentFragment?.findNavController()?.navigate(
                R.id.action_mainFragment_to_musicFragment
            ) ?: findNavController().navigate(R.id.action_mainFragment_to_musicFragment)
        }

        // 05: Nâng cấp ảnh
        binding.card05Upscale.setOnClickListener {
            parentFragment?.parentFragment?.findNavController()?.navigate(
                R.id.action_mainFragment_to_upscaleFragment
            ) ?: findNavController().navigate(R.id.action_mainFragment_to_upscaleFragment)
        }
    }
}
