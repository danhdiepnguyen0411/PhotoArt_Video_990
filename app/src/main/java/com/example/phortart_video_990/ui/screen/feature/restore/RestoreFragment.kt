package com.example.phortart_video_990.ui.screen.feature.restore

import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.core.utils.safePopBackStack
import com.example.phortart_video_990.databinding.FragmentRestoreBinding

class RestoreFragment : BaseFragment<FragmentRestoreBinding>(FragmentRestoreBinding::inflate) {

    override fun initView() {
        binding.header.setTitleRes(R.string.restore_title)
    }

    override fun initListener() {
        binding.header.setOnBackClickListener {
            if (isAdded) {
                findNavController().safePopBackStack()
            }
        }

        binding.btnStartRestore.setOnClickListener {
            if (isAdded) {
                findNavController().navigateSafe(R.id.action_restoreFragment_to_restorePickPhotoFragment)
            }
        }
    }
}
