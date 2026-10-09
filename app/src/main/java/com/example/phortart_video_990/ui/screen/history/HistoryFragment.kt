package com.example.phortart_video_990.ui.screen.history

import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.databinding.FragmentHistoryBinding
import com.example.phortart_video_990.ui.screen.template.CategoryAdapter

class HistoryFragment : BaseFragment<FragmentHistoryBinding>(FragmentHistoryBinding::inflate) {

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var historyAdapter: HistoryAdapter
    private var currentFilter = "Tất cả"

    private val filterTabs = listOf("Tất cả", "Tạo video", "Prompt AI", "Khôi phục")

    private val allHistoryItems = listOf(
        HistoryItemModel("h1", "Tạo video", "Kỷ niệm thanh xuân", "14:32 • 28/05/2026", R.drawable.history_1),
        HistoryItemModel("h2", "Prompt AI", "Chân dung nghệ thuật Cyberpunk", "11:15 • 27/05/2026", R.drawable.history_2),
        HistoryItemModel("h3", "Khôi phục", "Phục hồi ảnh chân dung gia đình", "09:40 • 26/05/2026", R.drawable.history_3),
        HistoryItemModel("h4", "Prompt AI", "Phong cảnh hoàng hôn rực rỡ", "18:20 • 25/05/2026", R.drawable.history_4),
        HistoryItemModel("h5", "Tạo video", "Chuyến du lịch mùa hè đáng nhớ", "16:05 • 24/05/2026", R.drawable.history_5)
    )

    override fun initView() {
        // Filter tabs
        categoryAdapter = CategoryAdapter(filterTabs, currentFilter) { selectedFilter ->
            currentFilter = selectedFilter
            applyFilter()
        }
        binding.rvHistoryFilter.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHistoryFilter.adapter = categoryAdapter

        // History items
        historyAdapter = HistoryAdapter(allHistoryItems, isGridMode = false) { item ->
            // Click item
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter

        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (currentFilter == "Tất cả") {
            allHistoryItems
        } else {
            allHistoryItems.filter { it.type.equals(currentFilter, ignoreCase = true) }
        }

        val isGrid = currentFilter != "Tất cả"
        binding.rvHistory.layoutManager = if (isGrid) {
            GridLayoutManager(requireContext(), 2)
        } else {
            LinearLayoutManager(requireContext())
        }

        historyAdapter.updateData(filtered, isGrid)
        binding.llEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}
