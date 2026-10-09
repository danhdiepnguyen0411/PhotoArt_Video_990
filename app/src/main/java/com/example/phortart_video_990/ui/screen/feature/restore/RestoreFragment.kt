package com.example.phortart_video_990.ui.screen.feature.restore

import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.databinding.FragmentRestoreBinding

class RestoreFragment : BaseFragment<FragmentRestoreBinding>(FragmentRestoreBinding::inflate) {

    override fun initView() {
        binding.tvTopTitle.text = getString(R.string.feature_restore_title).replace("\n", " ")
        binding.tvFunctionName.text = getString(R.string.feature_restore_title).replace("\n", " ")
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnStartRestore.setOnClickListener {
            Toast.makeText(requireContext(), "Đang phân tích và khôi phục ảnh...", Toast.LENGTH_SHORT).show()
        }
    }
}
