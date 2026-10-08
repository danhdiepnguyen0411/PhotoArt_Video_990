package com.example.phortart_video_990.ui.screen.feature.upscale

import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.databinding.FragmentUpscaleBinding

class UpscaleFragment : BaseFragment<FragmentUpscaleBinding>(FragmentUpscaleBinding::inflate) {

    override fun initView() {
        binding.tvFunctionName.text = getString(R.string.func_05_upscale)
        binding.tvTopTitle.text = getString(R.string.func_05_upscale)
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}
