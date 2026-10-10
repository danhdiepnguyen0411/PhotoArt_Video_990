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

    /**
     * Xóa mục lịch sử và xóa file video vật lý tương ứng trong App Documents (nếu có).
     */
    fun deleteHistoryItem(item: HistoryItemModel) {
        val current = getHistoryList().toMutableList()
        val removed = current.removeAll { it.title == item.title && it.date == item.date }
        if (removed) {
            prefs.historyJson = gson.toJson(current)
        }

        // Xóa file vật lý trong App Documents nếu imageUri/filePath trỏ tới file
        val path = item.imageUri ?: item.imageUrl
        if (!path.isNullOrBlank()) {
            try {
                val file = if (path.startsWith("file://")) {
                    java.io.File(java.net.URI(path))
                } else if (path.startsWith("/")) {
                    java.io.File(path)
                } else null

                if (file != null && file.exists() && file.absolutePath.contains("PhotoArtVideos")) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearHistory() {
        prefs.historyJson = null
    }
}
