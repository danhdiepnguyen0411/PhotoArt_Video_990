package com.aiart.photo.beauti.editor.data.api

import com.aiart.photo.beauti.editor.data.model.TemplateModel
import retrofit2.http.GET
import retrofit2.http.Query

interface TemplateApiService {
    @GET("api/v1/templates")
    suspend fun getTemplates(
        @Query("category") category: String? = null
    ): List<TemplateModel>
}
