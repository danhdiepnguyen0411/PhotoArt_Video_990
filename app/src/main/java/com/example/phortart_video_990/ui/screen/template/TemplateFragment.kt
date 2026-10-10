package com.example.phortart_video_990.ui.screen.template

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.phortart_video_990.core.utils.navigateSafe
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.databinding.FragmentTemplateBinding
import com.example.phortart_video_990.ui.screen.main.MainFragment
import kotlinx.coroutines.launch

class TemplateFragment : BaseFragment<FragmentTemplateBinding>(FragmentTemplateBinding::inflate) {

    private val viewModel: TemplateViewModel by viewModels()
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var templateAdapter: TemplateAdapter

    override fun initView() {
        setupTitleGradient()

        // Setup Dynamic Category Filter RecyclerView
        categoryAdapter = CategoryAdapter(
            categories = viewModel.categories.value,
            selectedCategoryCode = viewModel.selectedCategoryCode.value
        ) { selectedCategory ->
            viewModel.selectCategory(selectedCategory)
        }
        binding.rvTemplateCategories.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvTemplateCategories.adapter = categoryAdapter

        // Setup Template Grid (2 Columns)
        templateAdapter = TemplateAdapter(emptyList()) { selectedTemplate ->
            val bundle = android.os.Bundle().apply {
                putString(com.example.phortart_video_990.ui.screen.feature.createtemplate.CreateTemplateVideoFragment.ARG_TEMPLATE_CODE, selectedTemplate.id)
                putString(com.example.phortart_video_990.ui.screen.feature.createtemplate.CreateTemplateVideoFragment.ARG_TEMPLATE_TITLE, selectedTemplate.title)
                putString(com.example.phortart_video_990.ui.screen.feature.createtemplate.CreateTemplateVideoFragment.ARG_TEMPLATE_CATEGORY, selectedTemplate.category)
                putString(com.example.phortart_video_990.ui.screen.feature.createtemplate.CreateTemplateVideoFragment.ARG_TEMPLATE_THUMB, selectedTemplate.safeImageUrl.orEmpty())
            }
            val navController = parentFragment?.parentFragment?.findNavController() ?: findNavController()
            navController.navigateSafe(R.id.action_mainFragment_to_createTemplateVideoFragment, bundle)
        }
        binding.rvTemplates.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvTemplates.adapter = templateAdapter

        observeData()
    }

    private fun setupTitleGradient() {
        binding.tvTemplateHeaderTitle.post {
            val paint = binding.tvTemplateHeaderTitle.paint
            val width = paint.measureText(binding.tvTemplateHeaderTitle.text.toString())
            if (width > 0) {
                val textShader = LinearGradient(
                    0f, 0f, width, 0f,
                    intArrayOf(Color.parseColor("#4A7BFE"), Color.parseColor("#9153EE")),
                    null,
                    Shader.TileMode.CLAMP
                )
                binding.tvTemplateHeaderTitle.paint.shader = textShader
                binding.tvTemplateHeaderTitle.invalidate()
            }
        }
    }

    override fun initListener() {
        binding.btnTemplatePro.setOnClickListener {
            (parentFragment as? MainFragment)?.selectTab(3)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.categories.collect { categories ->
                        categoryAdapter.submitList(categories)
                    }
                }
                launch {
                    viewModel.selectedCategoryCode.collect { code ->
                        categoryAdapter.setSelected(code)
                    }
                }
                launch {
                    viewModel.templates.collect { list ->
                        templateAdapter.submitList(list)
                        val isLoading = viewModel.isLoading.value
                        binding.llEmptyState.visibility = if (list.isEmpty() && !isLoading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.pbLoading.visibility = if (loading) View.VISIBLE else View.GONE
                        if (loading) {
                            binding.llEmptyState.visibility = View.GONE
                        } else {
                            binding.llEmptyState.visibility = if (viewModel.templates.value.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        }
    }
}
