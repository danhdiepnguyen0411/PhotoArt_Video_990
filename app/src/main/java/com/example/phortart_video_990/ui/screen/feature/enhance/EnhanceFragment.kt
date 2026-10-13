package com.example.phortart_video_990.ui.screen.feature.enhance

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.core.utils.safePopBackStack
import com.example.phortart_video_990.databinding.FragmentEnhanceBinding

class EnhanceFragment : BaseFragment<FragmentEnhanceBinding>(FragmentEnhanceBinding::inflate) {

    companion object {
        const val KEY_INITIAL_PROMPT = "KEY_INITIAL_PROMPT"
    }

    private var selectedStyleIndex: Int = 1
    private var selectedStyleName: String = "Cyber Move"
    private var lastClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            selectedStyleIndex = savedInstanceState.getInt("KEY_SELECTED_STYLE_INDEX", selectedStyleIndex)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("KEY_SELECTED_STYLE_INDEX", selectedStyleIndex)
    }

    override fun initView() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            binding.topBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarInset 
            }
            binding.btnGeneratePrompt.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarInset + (24 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        val initialPrompt = arguments?.getString(KEY_INITIAL_PROMPT)
        if (!initialPrompt.isNullOrBlank() && binding.etPromptInput.text.isNullOrBlank()) {
            binding.etPromptInput.setText(initialPrompt)
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        binding.etPromptInput.filters = arrayOf(android.text.InputFilter.LengthFilter(500))
        binding.etPromptInput.doAfterTextChanged { text ->
            val count = (text?.length ?: 0).coerceAtMost(500)
            binding.tvPromptCounter.text = "$count/500"
            updateGenerateButtonState()
        }

        setupTopTitleGradient()
        updateSelectedStyle(selectedStyleIndex)
        updateGenerateButtonState()
    }

    override fun initListener() {
        binding.btnBack.setOnClickListener {
            if (isAdded) findNavController().safePopBackStack()
        }

        // Suggestion chips
        binding.chipAnimeGirl.setOnClickListener {
            binding.etPromptInput.setText(getString(R.string.prompt_anime_full))
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        binding.chipFantasy.setOnClickListener {
            binding.etPromptInput.setText(getString(R.string.prompt_fantasy_full))
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        binding.chipCutePet.setOnClickListener {
            binding.etPromptInput.setText(getString(R.string.prompt_pet_full))
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        binding.chipCyberpunk.setOnClickListener {
            binding.etPromptInput.setText(getString(R.string.prompt_cyberpunk_full))
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        binding.chipNature.setOnClickListener {
            binding.etPromptInput.setText(getString(R.string.prompt_nature_full))
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        binding.chipPortrait.setOnClickListener {
            binding.etPromptInput.setText(getString(R.string.prompt_portrait_full))
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        // Style Selection
        binding.cardStyle1.setOnClickListener { updateSelectedStyle(1) }
        binding.cardStyle2.setOnClickListener { updateSelectedStyle(2) }
        binding.cardStyle3.setOnClickListener { updateSelectedStyle(3) }

        // Start Generate
        binding.btnGeneratePrompt.setOnClickListener {
            val userPrompt = binding.etPromptInput.text?.toString()?.trim().orEmpty()
            if (userPrompt.isBlank()) {
                updateGenerateButtonState()
                return@setOnClickListener
            }

            if (SystemClock.elapsedRealtime() - lastClickTime < 600L) return@setOnClickListener
            lastClickTime = SystemClock.elapsedRealtime()

            // Combine user prompt with chosen style
            val fullPrompt = getString(R.string.prompt_style_suffix, userPrompt, selectedStyleName)

            findNavController().navigateSafe(
                R.id.action_enhanceFragment_to_enhanceLoadingFragment,
                bundleOf(
                    EnhanceLoadingFragment.KEY_USER_PROMPT to userPrompt,
                    EnhanceLoadingFragment.KEY_FULL_PROMPT to fullPrompt,
                    EnhanceLoadingFragment.KEY_STYLE_NAME to selectedStyleName
                )
            )
        }
    }

    private fun updateGenerateButtonState() {
        val hasPrompt = !binding.etPromptInput.text?.toString()?.trim().isNullOrEmpty()
        binding.btnGeneratePrompt.isEnabled = hasPrompt
        binding.btnGeneratePrompt.isClickable = hasPrompt
        binding.btnGeneratePrompt.alpha = if (hasPrompt) 1.0f else 0.4f
    }

    private fun setupTopTitleGradient() {
        val textView = binding.tvTopTitle
        textView.post {
            val width = textView.paint.measureText(textView.text.toString())
            if (width > 0f) {
                val shader = android.graphics.LinearGradient(
                    0f, 0f, width, 0f,
                    intArrayOf(
                        Color.parseColor("#3A8BF5"),
                        Color.parseColor("#7A63EE"),
                        Color.parseColor("#D45BD8")
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                textView.paint.shader = shader
                textView.invalidate()
            }
        }
    }

    private fun updateSelectedStyle(index: Int) {
        selectedStyleIndex = index
        val strokeWidth = (2.5f * resources.displayMetrics.density).toInt()
        val selectedColor = Color.parseColor("#7F8CF6")
        val transparentColor = Color.TRANSPARENT

        binding.cardStyle1.strokeWidth = if (index == 1) strokeWidth else 0
        binding.cardStyle1.strokeColor = if (index == 1) selectedColor else transparentColor

        binding.cardStyle2.strokeWidth = if (index == 2) strokeWidth else 0
        binding.cardStyle2.strokeColor = if (index == 2) selectedColor else transparentColor

        binding.cardStyle3.strokeWidth = if (index == 3) strokeWidth else 0
        binding.cardStyle3.strokeColor = if (index == 3) selectedColor else transparentColor

        selectedStyleName = when (index) {
            1 -> "Cyber Move"
            2 -> "Street Vibes"
            3 -> "Urban Renaissance"
            else -> "Cyber Move"
        }
    }
}
