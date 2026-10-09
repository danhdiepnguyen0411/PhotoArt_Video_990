package com.example.phortart_video_990.data.repository

import android.content.Context
import com.example.phortart_video_990.data.local.PreferencesManager
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HistoryRepository(context: Context) {

    private val prefs = PreferencesManager.getInstance(context)
    private val gson = Gson()

    fun getHistoryList(): List<HistoryItemModel> {
        val json = prefs.historyJson
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<HistoryItemModel>>() {}.type
            gson.fromJson<List<HistoryItemModel>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHistoryItem(item: HistoryItemModel) {
        val current = getHistoryList().toMutableList()
        current.add(0, item) // Add newest at top
        prefs.historyJson = gson.toJson(current)
    }

    fun clearHistory() {
        prefs.historyJson = null
    }
}
