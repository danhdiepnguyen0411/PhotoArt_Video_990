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

    private val categoryList = listOf(
        "Tất cả", "Hot", "Nhảy múa", "Anime", "Hiệu ứng", "Chân dung", "Ảo ảnh"
    )

    override fun initView() {
        // Setup Category Filter RecyclerView
        categoryAdapter = CategoryAdapter(categoryList, viewModel.selectedCategory.value) { selectedCat ->
            viewModel.selectCategory(selectedCat)
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
                    viewModel.templates.collect { list ->
                        templateAdapter.submitList(list)
                        binding.llEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.pbLoading.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }
}
