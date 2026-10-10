package com.example.phortart_video_990.ui.screen.history

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentHistoryBinding

class HistoryFragment : BaseFragment<FragmentHistoryBinding>(FragmentHistoryBinding::inflate) {

    private lateinit var filterAdapter: HistoryFilterAdapter
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var historyRepository: HistoryRepository
    private var currentFilter = "Tất cả"

    private val filterTabs = listOf(
        HistoryFilterItem("Tất cả", R.drawable.ic_filter_all),
        HistoryFilterItem("Gom ảnh", R.drawable.ic_filter_music),
        HistoryFilterItem("Mẫu AI", R.drawable.ic_filter_template),
        HistoryFilterItem("Prompt AI", R.drawable.ic_filter_sparkle),
        HistoryFilterItem("Khôi phục", R.drawable.ic_filter_restore)
    )

    private var userHistoryItems: List<HistoryItemModel> = emptyList()

    override fun initView() {
        historyRepository = HistoryRepository(requireContext())

        // Filter chips
        filterAdapter = HistoryFilterAdapter(filterTabs, currentFilter) { selectedFilter ->
            currentFilter = selectedFilter
            applyFilter()
        }
        binding.rvHistoryFilter.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHistoryFilter.adapter = filterAdapter

        // History items list (starts empty, loads real user data)
        historyAdapter = HistoryAdapter(
            items = emptyList(),
            onItemClick = { item ->
                HistoryPreviewDialog.newInstance(item) { deletedItem ->
                    historyRepository.deleteHistoryItem(deletedItem)
                    loadHistory()
                }.show(childFragmentManager, "HistoryPreviewDialog")
            }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        userHistoryItems = historyRepository.getHistoryList()
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (currentFilter == "Tất cả") {
            userHistoryItems
        } else {
            userHistoryItems.filter { it.type.equals(currentFilter, ignoreCase = true) }
        }

        historyAdapter.updateData(filtered)
        binding.llEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}
