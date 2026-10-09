package com.example.phortart_video_990.data.api

import com.example.phortart_video_990.data.model.TemplateModel
import retrofit2.http.GET
import retrofit2.http.Query

interface TemplateApiService {
    @GET("api/v1/templates")
    suspend fun getTemplates(
        @Query("category") category: String? = null
    ): List<TemplateModel>
}
