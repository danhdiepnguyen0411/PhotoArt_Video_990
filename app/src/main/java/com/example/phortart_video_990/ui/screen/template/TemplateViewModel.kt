package com.example.phortart_video_990.ui.screen.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    // Cache of all visual templates loaded from API
    private var allVisualTemplates: List<TemplateModel> = emptyList()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Fetch all visual templates first to know which categories actually have templates
                val allTemplates = repository.getTemplates(categoryCode = "ALL")
                allVisualTemplates = allTemplates

                // 2. Fetch categories from API
                val apiCategories = repository.getTemplateCategories()

                // Set of non-empty category identifiers found in actual templates
                val templateCategoriesPresent = allTemplates.map { it.category.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .toSet()

                // Only keep categories that have templates (and ALL), excluding AI TOOL
                val validCategories = apiCategories.filter { cat ->
                    if (cat.isAiTool) return@filter false
                    if (cat.code.equals("ALL", ignoreCase = true)) return@filter true
                    val codeMatch = cat.code.trim().lowercase() in templateCategoriesPresent
                    val nameMatch = cat.name.trim().lowercase() in templateCategoriesPresent
                    codeMatch || nameMatch
                }.toMutableList()

                // Also discover any extra categories present in templates that weren't in API categories
                val existingCodes = validCategories.map { it.code.trim().lowercase() }.toSet()
                allTemplates.forEach { tpl ->
                    val catCode = tpl.category.trim()
                    val isMusic = catCode.startsWith("MUSIC_", ignoreCase = true) || tpl.isMusic
                    val isAi = catCode.replace("_", " ").equals("AI TOOL", ignoreCase = true) ||
                            catCode.equals("AI_TOOL", ignoreCase = true)
                    if (catCode.isNotBlank() && !isMusic && !isAi && !existingCodes.contains(catCode.lowercase())) {
                        validCategories.add(CategoryModel(code = catCode, name = catCode, active = true))
                    }
                }

                _categories.value = validCategories

                // Filter templates for current selected category
                val currentCode = _selectedCategoryCode.value
                val selectedCat = validCategories.find { it.code.equals(currentCode, ignoreCase = true) }
                _templates.value = filterTemplatesByCategory(allTemplates, currentCode, selectedCat?.name)
            } catch (e: Exception) {
                _templates.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectCategory(category: CategoryModel) {
        if (_selectedCategoryCode.value != category.code) {
            _selectedCategoryCode.value = category.code
            loadTemplates(category.code, category.name)
        }
    }

    fun loadTemplates(categoryCode: String = _selectedCategoryCode.value, categoryName: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (allVisualTemplates.isNotEmpty()) {
                    _templates.value = filterTemplatesByCategory(allVisualTemplates, categoryCode, categoryName)
                } else {
                    val list = repository.getTemplates(categoryCode = categoryCode, categoryName = categoryName)
                    _templates.value = list
                }
            } catch (e: Exception) {
                _templates.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun filterTemplatesByCategory(
        templates: List<TemplateModel>,
        categoryCode: String,
        categoryName: String?
    ): List<TemplateModel> {
        val isAll = categoryCode.isBlank() ||
                categoryCode.equals("ALL", ignoreCase = true) ||
                categoryCode.equals("Tất cả", ignoreCase = true)
        if (isAll) return templates

        return templates.filter {
            it.category.equals(categoryCode, ignoreCase = true) ||
                    (!categoryName.isNullOrBlank() && it.category.equals(categoryName, ignoreCase = true))
        }
    }
}
