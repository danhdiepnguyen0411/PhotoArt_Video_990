package com.aiart.photo.beauti.editor.ui.screen.feature.restore

import androidx.navigation.fragment.findNavController
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.base.BaseFragment
import com.aiart.photo.beauti.editor.core.utils.navigateSafe
import com.aiart.photo.beauti.editor.core.utils.safePopBackStack
import com.aiart.photo.beauti.editor.databinding.FragmentRestoreBinding

import com.aiart.photo.beauti.editor.core.utils.setOnSingleClickListener

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

        binding.btnStartRestore.setOnSingleClickListener(700L) {
            if (isAdded) {
                findNavController().navigateSafe(R.id.action_restoreFragment_to_restorePickPhotoFragment)
            }
        }
    }
}
