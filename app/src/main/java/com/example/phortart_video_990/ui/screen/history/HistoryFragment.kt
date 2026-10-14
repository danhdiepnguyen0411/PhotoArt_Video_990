package com.example.phortart_video_990.ui.screen.history

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.repository.HistoryRepository
import com.example.phortart_video_990.databinding.FragmentHistoryBinding
import com.example.phortart_video_990.ui.screen.main.MainFragment
import com.google.gson.Gson

class HistoryFragment : BaseFragment<FragmentHistoryBinding>(FragmentHistoryBinding::inflate) {

    private lateinit var filterAdapter: HistoryFilterAdapter
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var historyRepository: HistoryRepository
    private var currentFilterKey = "ALL"

    private val filterTabs by lazy {
        listOf(
            HistoryFilterItem("ALL", getString(R.string.history_tab_all), R.drawable.ic_filter_all),
            HistoryFilterItem("VIDEO", getString(R.string.history_tab_video), R.drawable.ic_filter_music),
            HistoryFilterItem("PROMPT", getString(R.string.history_tab_prompt), R.drawable.ic_filter_sparkle),
            HistoryFilterItem("RESTORE", getString(R.string.history_tab_restore), R.drawable.ic_filter_restore)
        )
    }

    private var userHistoryItems: List<HistoryItemModel> = emptyList()

    override fun initView() {
        historyRepository = HistoryRepository(requireContext())

        // Filter chips
        filterAdapter = HistoryFilterAdapter(filterTabs, currentFilterKey) { selectedKey ->
            currentFilterKey = selectedKey
            applyFilter()
        }
        binding.rvHistoryFilter.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHistoryFilter.adapter = filterAdapter

        // History items list (starts empty, loads real user data)
        historyAdapter = HistoryAdapter(
            items = emptyList(),
            onItemClick = { item ->
                val navController = parentFragment?.parentFragment?.findNavController() ?: findNavController()
                val bundle = Bundle().apply {
                    putString(HistoryDetailFragment.KEY_HISTORY_ITEM_JSON, Gson().toJson(item))
                }
                navController.navigateSafe(R.id.action_mainFragment_to_historyDetailFragment, bundle)
            }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter

        binding.swipeRefreshLayout.setColorSchemeColors(
            android.graphics.Color.parseColor("#4A7BFE"),
            android.graphics.Color.parseColor("#9153EE")
        )
        binding.swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            binding.rvHistory.canScrollVertically(-1)
        }
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadHistory()
            binding.swipeRefreshLayout.postDelayed({
                if (isAdded) binding.swipeRefreshLayout.isRefreshing = false
            }, 500L)
        }
    }

    override fun initListener() {
        binding.headerHistory.setOnProClickListener {
            (parentFragment as? MainFragment)?.selectTab(3)
        }
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
        val filtered = when (currentFilterKey) {
            "VIDEO" -> userHistoryItems.filter {
                it.type.contains("video", ignoreCase = true) ||
                        it.type.contains("Gom", ignoreCase = true) ||
                        it.type.contains("Template", ignoreCase = true) ||
                        it.isVideo
            }
            "PROMPT" -> userHistoryItems.filter {
                it.type.contains("prompt", ignoreCase = true) ||
                        it.type.contains("nâng cấp", ignoreCase = true) ||
                        it.type.contains("enhance", ignoreCase = true)
            }
            "RESTORE" -> userHistoryItems.filter {
                it.type.contains("phục", ignoreCase = true) ||
                        it.type.contains("restore", ignoreCase = true)
            }
            else -> userHistoryItems
        }

        val isGrid = currentFilterKey != "ALL"
        if (isGrid) {
            if (binding.rvHistory.layoutManager !is GridLayoutManager) {
                binding.rvHistory.layoutManager = GridLayoutManager(requireContext(), 3)
            }
            val padHorizontal = (16 * resources.displayMetrics.density).toInt()
            binding.rvHistory.setPadding(padHorizontal, binding.rvHistory.paddingTop, padHorizontal, binding.rvHistory.paddingBottom)
        } else {
            if (binding.rvHistory.layoutManager !is LinearLayoutManager || binding.rvHistory.layoutManager is GridLayoutManager) {
                binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
            }
            val padHorizontal = (20 * resources.displayMetrics.density).toInt()
            binding.rvHistory.setPadding(padHorizontal, binding.rvHistory.paddingTop, padHorizontal, binding.rvHistory.paddingBottom)
        }

        historyAdapter.setGridMode(isGrid)
        historyAdapter.updateData(filtered)
        binding.llEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}
