package com.example.phortart_video_990.ui.screen.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.data.repository.TemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TemplateViewModel(
    private val repository: TemplateRepository = TemplateRepository()
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("Tất cả")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _templates = MutableStateFlow<List<TemplateModel>>(emptyList())
    val templates: StateFlow<List<TemplateModel>> = _templates.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadTemplates("Tất cả")
    }

    fun selectCategory(category: String) {
        if (_selectedCategory.value != category) {
            _selectedCategory.value = category
            loadTemplates(category)
        }
    }

    fun loadTemplates(category: String = _selectedCategory.value) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list = repository.getTemplates(category)
                _templates.value = list
            } catch (e: Exception) {
                _templates.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
