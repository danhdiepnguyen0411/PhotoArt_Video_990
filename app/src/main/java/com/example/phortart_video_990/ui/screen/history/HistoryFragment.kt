package com.example.phortart_video_990.ui.screen.history

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.databinding.FragmentHistoryBinding

class HistoryFragment : BaseFragment<FragmentHistoryBinding>(FragmentHistoryBinding::inflate) {

    private lateinit var filterAdapter: HistoryFilterAdapter
    private lateinit var historyAdapter: HistoryAdapter
    private var currentFilter = "Tất cả"

    private val filterTabs = listOf(
        HistoryFilterItem("Tất cả", R.drawable.ic_filter_all),
        HistoryFilterItem("Gom ảnh", R.drawable.ic_filter_music),
        HistoryFilterItem("Prompt AI", R.drawable.ic_filter_sparkle),
        HistoryFilterItem("Khôi phục", R.drawable.ic_filter_restore)
    )

    private val allHistoryItems = listOf(
        HistoryItemModel("h1", "Khôi phục", "Nâng cấp ảnh", "26 thg 6, 2024 • 14:32", R.drawable.history_1),
        HistoryItemModel("h2", "Khôi phục", "Khôi phục ảnh cũ", "26 thg 6, 2024 • 11:45", R.drawable.history_2),
        HistoryItemModel("h3", "Prompt AI", "Tạo ảnh chân dung", "25 thg 6, 2024 • 16:20", R.drawable.history_3),
        HistoryItemModel("h4", "Gom ảnh", "Gom ảnh tạo video", "24 thg 6, 2024 • 09:30", R.drawable.history_4),
        HistoryItemModel("h5", "Khôi phục", "Làm rõ nét ảnh", "22 thg 6, 2024 • 16:18", R.drawable.history_5),
        HistoryItemModel("h6", "Prompt AI", "Anime girl cute", "20 thg 6, 2024 • 13:05", R.drawable.suggestion_ai_portrait),
        HistoryItemModel("h7", "Khôi phục", "Khôi phục màu sắc", "18 thg 6, 2024 • 10:42", R.drawable.suggestion_ai_style),
        HistoryItemModel("h8", "Gom ảnh", "Video kỷ niệm hè", "15 thg 6, 2024 • 08:15", R.drawable.suggestion_ai_dance)
    )

    override fun initView() {
        // Filter chips
        filterAdapter = HistoryFilterAdapter(filterTabs, currentFilter) { selectedFilter ->
            currentFilter = selectedFilter
            applyFilter()
        }
        binding.rvHistoryFilter.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHistoryFilter.adapter = filterAdapter

        // History items list
        historyAdapter = HistoryAdapter(allHistoryItems) { item ->
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

        historyAdapter.updateData(filtered)
        binding.llEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}
