package com.aiart.photo.beauti.editor.ui.screen.feature.upscale

import androidx.navigation.fragment.findNavController
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.base.BaseFragment
import com.aiart.photo.beauti.editor.databinding.FragmentUpscaleBinding

class UpscaleFragment : BaseFragment<FragmentUpscaleBinding>(FragmentUpscaleBinding::inflate) {

    override fun initView() {
        binding.tvFunctionName.text = getString(R.string.func_05_upscale)
    }

    override fun initListener() {
        binding.header.setOnBackClickListener {
            findNavController().popBackStack()
        }
    }
}
