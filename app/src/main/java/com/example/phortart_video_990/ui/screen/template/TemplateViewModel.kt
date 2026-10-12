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

    // All music tracks from API
    private var allMusicList: List<TemplateModel> = emptyList()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Fetch real music tracks from API
                var realMusicTracks = repository.getMusicTracks(categoryCode = "ALL")
                if (realMusicTracks.isEmpty()) {
                    realMusicTracks = repository.getDefaultMusicTracks()
                }
                allMusicList = realMusicTracks

                // 2. Fetch music categories from API
                val apiMusicCategories = repository.getMusicCategories()
                val categoriesList = mutableListOf(CategoryModel.ALL)

                val validMusicCategories = apiMusicCategories.filter {
                    it.active && it.name.isNotBlank() &&
                            !it.code.equals("ALL", ignoreCase = true) &&
                            !it.name.equals("ALL", ignoreCase = true) &&
                            !it.name.equals("Tất cả", ignoreCase = true)
                }
                categoriesList.addAll(validMusicCategories)

                // 3. Dynamically discover any music categories from music tracks
                val existingCodes = categoriesList.map { it.code.lowercase() }.toMutableSet()
                existingCodes.addAll(categoriesList.map { it.name.lowercase() })

                realMusicTracks.forEach { item ->
                    val cat = item.category.trim()
                    if (cat.isNotBlank()) {
                        val cleanCat = cat.removePrefix("MUSIC_")
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
                _templates.value = filterMusic(realMusicTracks, currentCode, selectedCat?.name)
            } catch (e: Exception) {
                val fallback = repository.getDefaultMusicTracks()
                allMusicList = fallback
                _templates.value = fallback
                _categories.value = repository.getDefaultMusicCategories()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectCategory(category: CategoryModel) {
        if (_selectedCategoryCode.value != category.code) {
            _selectedCategoryCode.value = category.code
            _templates.value = filterMusic(allMusicList, category.code, category.name)
        }
    }

    private fun filterMusic(
        list: List<TemplateModel>,
        categoryCode: String,
        categoryName: String?
    ): List<TemplateModel> {
        val isAll = categoryCode.isBlank() ||
                categoryCode.equals("ALL", ignoreCase = true) ||
                categoryCode.equals("Tất cả", ignoreCase = true)
        if (isAll) return list

        val target = categoryCode.trim()
        val cleanTarget = target.removePrefix("MUSIC_").replace("_", " ").lowercase()

        return list.filter { item ->
            val itemCat = item.category.trim()
            val cleanItemCat = itemCat.removePrefix("MUSIC_").replace("_", " ").lowercase()
            cleanItemCat == cleanTarget ||
                    itemCat.equals(target, ignoreCase = true) ||
                    (!categoryName.isNullOrBlank() && itemCat.equals(categoryName, ignoreCase = true))
        }
    }
}
