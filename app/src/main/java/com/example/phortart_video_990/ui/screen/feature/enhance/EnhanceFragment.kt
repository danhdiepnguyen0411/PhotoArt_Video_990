package com.example.phortart_video_990.ui.screen.feature.enhance

import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.core.utils.safePopBackStack
import com.example.phortart_video_990.databinding.FragmentEnhanceBinding

class EnhanceFragment : BaseFragment<FragmentEnhanceBinding>(FragmentEnhanceBinding::inflate) {

    companion object {
        const val KEY_INITIAL_PROMPT = "KEY_INITIAL_PROMPT"
        const val KEY_INITIAL_STYLE = "KEY_INITIAL_STYLE"
    }

    private var selectedStyleIndex: Int = -1
    private var selectedStyleName: String = "Cyber Move"
    private var lastClickTime = 0L
    private lateinit var styleAdapter: EnhanceStyleAdapter

    private val stylesList by lazy {
        listOf(
            EnhanceStyleModel(
                id = "cyber_move",
                title = "Cyber Move",
                imageResId = R.drawable.cyber_move,
                prompt = "A dynamic cyber-style dancer frozen mid-move, glowing neon outlines, motion trails, futuristic city lights, cinematic, ultra detailed",
                isHot = true
            ),
            EnhanceStyleModel(
                id = "street_vibes",
                title = "Street Vibes",
                imageResId = R.drawable.street_vibes,
                prompt = "Street vibes portrait of a young person in trendy streetwear, graffiti walls, golden hour light, urban photography, 35mm film look",
                isHot = false
            ),
            EnhanceStyleModel(
                id = "urban_renaissance",
                title = "Urban\nRenaissance",
                imageResId = R.drawable.urban_renaissance,
                prompt = "Urban renaissance cityscape at dusk, classical architecture blended with modern skyscrapers, warm light, painterly style, highly detailed",
                isHot = false
            ),
            EnhanceStyleModel(
                id = "dreamy",
                title = "Dreamy",
                imageResId = R.drawable.dreamy,
                prompt = "A dreamy pastel landscape with soft clouds, floating petals and glowing light, ethereal atmosphere, soft focus, fantasy illustration",
                isHot = false
            ),
            EnhanceStyleModel(
                id = "vintage",
                title = "Vintage",
                imageResId = R.drawable.vintage,
                prompt = "Vintage 1970s film photograph of a couple on a seaside road trip, warm faded colors, light leaks, film grain, nostalgic mood",
                isHot = false
            ),
            EnhanceStyleModel(
                id = "cyberpunk",
                title = "Cyberpunk",
                imageResId = R.drawable.cyberpunk,
                prompt = "A neon-lit cyberpunk metropolis at night with flying cars, holographic ads and rain-soaked streets, cinematic, 8k",
                isHot = false
            ),
            EnhanceStyleModel(
                id = "nature",
                title = "Nature",
                imageResId = R.drawable.nature,
                prompt = "A magical hidden waterfall nestled in a lush sunlit pine forest, drifting mist, god rays, hyperrealistic nature photography",
                isHot = false
            ),
            EnhanceStyleModel(
                id = "portrait",
                title = "Portrait",
                imageResId = R.drawable.portrait,
                prompt = "A cinematic hyperrealistic portrait of a girl in a flower field, soft sunlight, shallow depth of field, 8k",
                isHot = false
            ),
            EnhanceStyleModel(
                id = "fantasy",
                title = "Fantasy",
                imageResId = R.drawable.fantasy,
                prompt = "A floating magical castle above the clouds with glowing crystals and an aurora in the sky, epic fantasy concept art",
                isHot = false
            ),
            EnhanceStyleModel(
                id = "selfie",
                title = "Selfie",
                imageResId = R.drawable.selfie,
                prompt = "A cheerful stylish selfie of a young person in a trendy outfit, soft natural light, bright background, social media aesthetic, sharp details",
                isHot = false
            )
        )
    }

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
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            binding.btnGeneratePrompt.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navBarInset + (24 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        // Setup horizontal styles list
        styleAdapter = EnhanceStyleAdapter(
            items = stylesList,
            selectedIndex = selectedStyleIndex
        ) { selectedStyle ->
            selectedStyleName = selectedStyle.title.replace("\n", " ")
            selectedStyleIndex = styleAdapter.getSelectedIndex()
            binding.etPromptInput.setText(selectedStyle.prompt)
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)
        }

        binding.rvStyles.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.rvStyles.adapter = styleAdapter

        binding.etPromptInput.filters = arrayOf(android.text.InputFilter.LengthFilter(500))
        binding.etPromptInput.doAfterTextChanged {
            updatePromptCounter()
            updateGenerateButtonState()
        }

        val initialPrompt = arguments?.getString(KEY_INITIAL_PROMPT)
        val initialStyle = arguments?.getString(KEY_INITIAL_STYLE)
        if (!initialPrompt.isNullOrBlank() && binding.etPromptInput.text.isNullOrBlank()) {
            binding.etPromptInput.setText(initialPrompt)
            binding.etPromptInput.setSelection(binding.etPromptInput.text.length)

            val matchedIdx = findMatchingStyleIndex(initialPrompt, initialStyle)
            if (matchedIdx != -1) {
                selectedStyleIndex = matchedIdx
                styleAdapter.setSelectedIndex(matchedIdx)
                selectedStyleName = stylesList[matchedIdx].title.replace("\n", " ")
                binding.rvStyles.post {
                    binding.rvStyles.scrollToPosition(matchedIdx)
                }
            }
        }

        updatePromptCounter()
        updateGenerateButtonState()
    }

    override fun initListener() {
        binding.header.setOnBackClickListener {
            if (isAdded) findNavController().safePopBackStack()
        }


        // Start Generate
        binding.btnGeneratePrompt.setOnClickListener {
            val userPrompt = binding.etPromptInput.text?.toString()?.trim().orEmpty()
            if (userPrompt.isBlank()) {
                updateGenerateButtonState()
                return@setOnClickListener
            }

            if (SystemClock.elapsedRealtime() - lastClickTime < 600L) return@setOnClickListener
            lastClickTime = SystemClock.elapsedRealtime()

            // Combine user prompt with chosen style if not already present
            val fullPrompt = if (userPrompt.contains(selectedStyleName, ignoreCase = true)) {
                userPrompt
            } else {
                getString(R.string.prompt_style_suffix, userPrompt, selectedStyleName)
            }

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

    private fun updatePromptCounter() {
        val count = (binding.etPromptInput.text?.length ?: 0).coerceAtMost(500)
        binding.tvPromptCounter.text = "$count/500"
    }

    private fun updateGenerateButtonState() {
        val hasPrompt = !binding.etPromptInput.text?.toString()?.trim().isNullOrEmpty()
        binding.btnGeneratePrompt.isEnabled = hasPrompt
        binding.btnGeneratePrompt.isClickable = hasPrompt
        binding.btnGeneratePrompt.alpha = if (hasPrompt) 1.0f else 0.4f
    }

    private fun findMatchingStyleIndex(prompt: String, styleHint: String?): Int {
        // Priority 1: Match directly by styleHint
        val cleanHint = styleHint?.trim().orEmpty()
        if (cleanHint.isNotBlank()) {
            val byHint = stylesList.indexOfFirst {
                val titleNorm = it.title.replace("\n", " ")
                titleNorm.equals(cleanHint, ignoreCase = true) ||
                        it.id.equals(cleanHint, ignoreCase = true) ||
                        cleanHint.contains(titleNorm, ignoreCase = true) ||
                        cleanHint.contains(it.id.replace("_", " "), ignoreCase = true)
            }
            if (byHint != -1) return byHint
        }

        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) return -1

        // Priority 2: Exact prompt match
        val exactIdx = stylesList.indexOfFirst {
            it.prompt.trim().equals(cleanPrompt, ignoreCase = true)
        }
        if (exactIdx != -1) return exactIdx

        // Priority 3: Prompt contains the style's prompt, or style's prompt contains prompt
        val containsIdx = stylesList.indexOfFirst {
            cleanPrompt.contains(it.prompt.trim(), ignoreCase = true) ||
                    it.prompt.trim().contains(cleanPrompt, ignoreCase = true)
        }
        if (containsIdx != -1) return containsIdx

        // Priority 4: Prompt mentions the style title or ID
        val byTitle = stylesList.indexOfFirst {
            val titleNorm = it.title.replace("\n", " ")
            cleanPrompt.contains(titleNorm, ignoreCase = true) ||
                    cleanPrompt.contains(it.id.replace("_", " "), ignoreCase = true)
        }
        if (byTitle != -1) return byTitle

        // Priority 5: Signature keywords / phrases in Vietnamese & English
        val lower = cleanPrompt.lowercase()
        return when {
            lower.contains("cyber-style") || lower.contains("cyber move") || lower.contains("neon outline") || lower.contains("motion trail") || lower.contains("cyber_move") -> {
                stylesList.indexOfFirst { it.id == "cyber_move" }
            }
            lower.contains("street vibe") || lower.contains("streetwear") || lower.contains("graffiti") || lower.contains("35mm film") || lower.contains("street_vibes") -> {
                stylesList.indexOfFirst { it.id == "street_vibes" }
            }
            lower.contains("urban renaissance") || lower.contains("classical architecture") || lower.contains("skyscraper") || lower.contains("urban_renaissance") -> {
                stylesList.indexOfFirst { it.id == "urban_renaissance" }
            }
            lower.contains("dreamy") || lower.contains("pastel landscape") || lower.contains("soft clouds") || lower.contains("floating petal") || lower.contains("mơ màng") -> {
                stylesList.indexOfFirst { it.id == "dreamy" }
            }
            lower.contains("vintage") || lower.contains("1970") || lower.contains("road trip") || lower.contains("faded color") || lower.contains("film grain") || lower.contains("cổ điển") -> {
                stylesList.indexOfFirst { it.id == "vintage" }
            }
            lower.contains("cyberpunk") || lower.contains("flying car") || lower.contains("holographic") || lower.contains("chiến binh cyberpunk") -> {
                stylesList.indexOfFirst { it.id == "cyberpunk" }
            }
            lower.contains("waterfall") || lower.contains("thác nước") || lower.contains("pine forest") || lower.contains("rừng cây") || lower.contains("thiên nhiên") || lower.contains("nature") -> {
                stylesList.indexOfFirst { it.id == "nature" }
            }
            lower.contains("flower field") || lower.contains("cánh đồng hoa") || lower.contains("portrait") || lower.contains("chân dung") || lower.contains("anime girl") -> {
                stylesList.indexOfFirst { it.id == "portrait" }
            }
            lower.contains("floating magical castle") || lower.contains("lâu đài kỳ ảo") || lower.contains("aurora") || lower.contains("thần tiên") || lower.contains("fantasy") -> {
                stylesList.indexOfFirst { it.id == "fantasy" }
            }
            lower.contains("selfie") || lower.contains("cheerful stylish selfie") || lower.contains("social media aesthetic") || lower.contains("chụp selfie") -> {
                stylesList.indexOfFirst { it.id == "selfie" }
            }
            else -> -1
        }
    }
}
