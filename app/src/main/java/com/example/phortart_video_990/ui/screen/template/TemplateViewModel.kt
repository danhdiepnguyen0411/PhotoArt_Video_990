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

    private val _categories = MutableStateFlow<List<CategoryModel>>(emptyList())
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

                // 2. Fetch dancing categories from API
                val apiDancingCategories = repository.getDancingCategories()
                val categoriesList = mutableListOf(CategoryModel.ALL)

                val validDancingCategories = apiDancingCategories.filter {
                    it.active && it.name.isNotBlank() &&
                            !it.code.equals("ALL", ignoreCase = true) &&
                            !it.name.equals("ALL", ignoreCase = true) &&
                            !it.name.equals("Tất cả", ignoreCase = true)
                }
                categoriesList.addAll(validDancingCategories)

                // 3. Dynamically discover any dancing categories from templates
                val existingCodes = categoriesList.map { it.code.lowercase() }.toMutableSet()
                existingCodes.addAll(categoriesList.map { it.name.lowercase() })

                realDancingTemplates.forEach { item ->
                    val cat = item.category.trim()
                    if (cat.isNotBlank() && !cat.startsWith("MUSIC_", ignoreCase = true)) {
                        val cleanCat = cat.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                        if (!existingCodes.contains(cat.lowercase()) && !existingCodes.contains(cleanCat.lowercase())) {
                            existingCodes.add(cat.lowercase())
                            existingCodes.add(cleanCat.lowercase())
                            categoriesList.add(CategoryModel(code = cat, name = cleanCat, active = true))
                        }
                    }
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
