package com.example.phortart_video_990.ui.screen.feature.restore

import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.databinding.FragmentRestoreBinding

class RestoreFragment : BaseFragment<FragmentRestoreBinding>(FragmentRestoreBinding::inflate) {

    override fun initView() {
        binding.tvFunctionName.text = getString(R.string.func_02_restore)
        binding.tvTopTitle.text = getString(R.string.func_02_restore)
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}
