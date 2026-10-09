package com.example.phortart_video_990.ui.screen.template

import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
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
            // Navigate to feature create video with template
            parentFragment?.parentFragment?.findNavController()?.navigate(
                R.id.action_mainFragment_to_memoriesFragment
            ) ?: findNavController().navigate(R.id.action_mainFragment_to_memoriesFragment)
        }
        binding.rvTemplates.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvTemplates.adapter = templateAdapter

        observeData()
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
