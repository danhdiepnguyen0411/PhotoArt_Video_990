package com.example.phortart_video_990.ui.screen.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.model.CategoryModel
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.data.repository.TemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TemplateViewModel(
    private val repository: TemplateRepository = TemplateRepository()
) : ViewModel() {

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            CategoryModel.ALL,
            CategoryModel(code = "HOT", name = "Hot Trend", active = true),
            CategoryModel(code = "TIKTOK", name = "TikTok Dance", active = true),
            CategoryModel(code = "KPOP", name = "K-Pop", active = true)
        )
    }

    private val _categories = MutableStateFlow<List<CategoryModel>>(DEFAULT_CATEGORIES)
    val categories: StateFlow<List<CategoryModel>> = _categories.asStateFlow()

    private val _selectedCategoryCode = MutableStateFlow("ALL")
    val selectedCategoryCode: StateFlow<String> = _selectedCategoryCode.asStateFlow()

    private val _templates = MutableStateFlow<List<TemplateModel>>(emptyList())
    val templates: StateFlow<List<TemplateModel>> = _templates.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isError = MutableStateFlow(false)
    val isError: StateFlow<Boolean> = _isError.asStateFlow()

    // All dancing templates from API
    private var allDancingList: List<TemplateModel> = emptyList()

    init {
        loadData()
    }

    fun loadData() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Fetch real dancing templates from API
                val realDancingTemplates = repository.getDancingTemplates()
                allDancingList = realDancingTemplates

                // 2. Extract categories directly from templates list (lấy chuẩn từ danh sách, không fix cứng)
                val categoriesList = mutableListOf(CategoryModel.ALL)
                val existingCodes = mutableSetOf("all", "tất cả")

                realDancingTemplates.forEach { item ->
                    val cat = item.category.trim()
                    if (cat.isNotBlank() && !cat.startsWith("MUSIC_", ignoreCase = true)) {
                        val cleanCat = item.categoryDisplayName.ifBlank {
                            cat.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                        }
                        if (existingCodes.add(cat.lowercase())) {
                            categoriesList.add(CategoryModel(code = cat, name = cleanCat, active = true))
                        }
                    }
                }

                if (categoriesList.size <= 1) {
                    val apiDancingCategories = repository.getDancingCategories()
                    val validDancingCategories = apiDancingCategories.filter {
                        it.active && it.name.isNotBlank() &&
                                !it.code.equals("ALL", ignoreCase = true) &&
                                !it.name.equals("ALL", ignoreCase = true) &&
                                !it.name.equals("Tất cả", ignoreCase = true)
                    }
                    categoriesList.addAll(validDancingCategories)
                }

                _categories.value = categoriesList

                // Filter for current selected category
                val currentCode = _selectedCategoryCode.value
                val selectedCat = categoriesList.find { it.code.equals(currentCode, ignoreCase = true) }
                _templates.value = filterTemplates(realDancingTemplates, currentCode, selectedCat?.name)
                _isError.value = false
            } catch (e: Exception) {
                _isError.value = true
                allDancingList = emptyList()
                _templates.value = emptyList()
                _categories.value = repository.getDefaultDancingCategories()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectCategory(category: CategoryModel) {
        if (_selectedCategoryCode.value != category.code) {
            _selectedCategoryCode.value = category.code
            _templates.value = filterTemplates(allDancingList, category.code, category.name)
        }
    }

    private fun filterTemplates(
        list: List<TemplateModel>,
        categoryCode: String,
        categoryName: String?
    ): List<TemplateModel> {
        val isAll = categoryCode.isBlank() ||
                categoryCode.equals("ALL", ignoreCase = true) ||
                categoryCode.equals("Tất cả", ignoreCase = true)
        if (isAll) return list

        val target = categoryCode.trim().lowercase()
        val cleanTarget = target.replace("_", " ")

        return list.filter { item ->
            val itemCat = item.category.trim().lowercase()
            val cleanItemCat = itemCat.replace("_", " ")
            cleanItemCat == cleanTarget ||
                    itemCat == target ||
                    (!categoryName.isNullOrBlank() && itemCat == categoryName.trim().lowercase()) ||
                    itemCat.contains(target) ||
                    (!categoryName.isNullOrBlank() && itemCat.contains(categoryName.trim().lowercase()))
        }
    }
}
