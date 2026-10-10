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

    private val _categories = MutableStateFlow<List<CategoryModel>>(repository.getDefaultCategories())
    val categories: StateFlow<List<CategoryModel>> = _categories.asStateFlow()

    private val _selectedCategoryCode = MutableStateFlow("ALL")
    val selectedCategoryCode: StateFlow<String> = _selectedCategoryCode.asStateFlow()

    private val _templates = MutableStateFlow<List<TemplateModel>>(repository.getDefaultTemplates())
    val templates: StateFlow<List<TemplateModel>> = _templates.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Load ONLY visual template categories from API (excludes music)
                val apiCategories = repository.getTemplateCategories()
                _categories.value = apiCategories

                // 2. Load templates for currently selected category
                val currentCode = _selectedCategoryCode.value
                val selectedCat = apiCategories.find { it.code.equals(currentCode, ignoreCase = true) }
                val templateList = repository.getTemplates(categoryCode = currentCode, categoryName = selectedCat?.name)
                _templates.value = templateList

                // 3. Dynamic category discovery from templates
                discoverExtraCategories(templateList)
            } catch (e: Exception) {
                // Keep default templates on error
                if (_templates.value.isEmpty()) {
                    _templates.value = repository.getDefaultTemplates()
                }
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
                val list = repository.getTemplates(categoryCode = categoryCode, categoryName = categoryName)
                _templates.value = list
                discoverExtraCategories(list)
            } catch (e: Exception) {
                if (_templates.value.isEmpty()) {
                    _templates.value = repository.getDefaultTemplates()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun discoverExtraCategories(templates: List<TemplateModel>) {
        val currentCats = _categories.value.toMutableList()
        var updated = false
        val existingCodes = currentCats.map { it.code.lowercase() }.toSet()

        templates.forEach { tpl ->
            val code = tpl.category.trim()
            val isMusic = code.startsWith("MUSIC_", ignoreCase = true) || tpl.isMusic
            if (code.isNotBlank() && !isMusic && !existingCodes.contains(code.lowercase())) {
                currentCats.add(CategoryModel(code = code, name = code, active = true))
                updated = true
            }
        }
        if (updated) {
            _categories.value = currentCats
        }
    }
}
