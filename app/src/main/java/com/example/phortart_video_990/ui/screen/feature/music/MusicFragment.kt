package com.example.phortart_video_990.ui.screen.feature.music

import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.databinding.FragmentMusicBinding

class MusicFragment : BaseFragment<FragmentMusicBinding>(FragmentMusicBinding::inflate) {

    override fun initView() {
        binding.tvFunctionName.text = getString(R.string.func_04_music)
        binding.tvTopTitle.text = getString(R.string.func_04_music)
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}
