package com.example.phortart_video_990.ui.screen.splash

import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.databinding.FragmentSplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashFragment : BaseFragment<FragmentSplashBinding>(FragmentSplashBinding::inflate) {

    override fun initView() {
        binding.progressBar.progress = 0
    }

    override fun initData() {
        startProgressSimulation()
    }

    private fun startProgressSimulation() {
        viewLifecycleOwner.lifecycleScope.launch {
            var progress = 0
            while (progress <= 100) {
                binding.progressBar.progress = progress
                delay(25L)
                progress += 2
            }
            navigateToNext()
        }
    }

    private fun navigateToNext() {
        if (!isAdded) return
        val prefs = PreferencesManager.getInstance(requireContext())
        when {
            !prefs.isFirstLanguageSelected -> {
                findNavController().navigate(R.id.action_splashFragment_to_languageFragment)
            }
            !prefs.hasCompletedIntro -> {
                findNavController().navigate(R.id.action_splashFragment_to_introFragment)
            }
            else -> {
                findNavController().navigate(R.id.action_splashFragment_to_mainFragment)
            }
        }
    }
}
