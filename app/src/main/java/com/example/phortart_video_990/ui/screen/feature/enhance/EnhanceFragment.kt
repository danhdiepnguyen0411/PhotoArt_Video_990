package com.example.phortart_video_990.ui.screen.feature.enhance

import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.databinding.FragmentEnhanceBinding

class EnhanceFragment : BaseFragment<FragmentEnhanceBinding>(FragmentEnhanceBinding::inflate) {

    override fun initView() {
        binding.tvFunctionName.text = getString(R.string.func_03_enhance)
        binding.tvTopTitle.text = getString(R.string.func_03_enhance)
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}
