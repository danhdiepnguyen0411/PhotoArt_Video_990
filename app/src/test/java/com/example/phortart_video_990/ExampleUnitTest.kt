package com.example.phortart_video_990

import com.example.phortart_video_990.data.model.CategoryModel
import com.example.phortart_video_990.data.model.HistoryItemModel
import com.example.phortart_video_990.data.model.TemplateModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    private val gson = Gson()

    @Test
    fun categoryModel_dynamicNamingAndCode() {
        val cat1 = CategoryModel(code = "AI_DANCE", name = "Vũ Điệu Mới")
        assertEquals("Vũ Điệu Mới", cat1.displayName)
        assertEquals("AI_DANCE", cat1.code)

        // If backend renames category name or changes code
        val catRenamed = CategoryModel(code = "AI_DANCE", name = "Dance Transition Trend")
        assertEquals("Dance Transition Trend", catRenamed.displayName)

        // If name is blank, falls back to code
        val catNoName = CategoryModel(code = "HIP_HOP", name = "")
        assertEquals("HIP_HOP", catNoName.displayName)

        assertEquals("Tất cả", CategoryModel.ALL.displayName)
    }

    @Test
    fun templateModel_apiJsonParsingWithSafeImageUrl() {
        val json = """
            {
                "code": "video_face_swap_futuristic",
                "name": "Futuristic Dance",
                "description": "Vibrant K-pop dance",
                "imageUrl": "\thttps://i.pinimg.com/image.jpg",
                "videoUrl": "https://v1.pinimg.com/video.mp4",
                "categoryCode": "AI_STYLE",
                "premium": false
            }
        """.trimIndent()

        val template = gson.fromJson(json, TemplateModel::class.java)
        assertEquals("video_face_swap_futuristic", template.id)
        assertEquals("Futuristic Dance", template.title)
        assertEquals("AI_STYLE", template.category)
        // Ensure leading tab is safely stripped
        assertEquals("https://i.pinimg.com/image.jpg", template.safeImageUrl)
        assertFalse(template.premium)
    }

    @Test
    fun categoryJsonParsing_dynamicListFromApi() {
        val json = """
            [
                {"code": "TRENDING", "name": "Xu Hướng", "active": true, "orderPosition": 0},
                {"code": "AI_DANCE", "name": "Dance Remix", "active": true, "orderPosition": 1}
            ]
        """.trimIndent()

        val type = object : TypeToken<List<CategoryModel>>() {}.type
        val categories: List<CategoryModel> = gson.fromJson(json, type)

        assertEquals(2, categories.size)
        assertEquals("Xu Hướng", categories[0].name)
        assertEquals("TRENDING", categories[0].code)
        assertEquals("Dance Remix", categories[1].name)
        assertEquals("AI_DANCE", categories[1].code)
    }

    @Test
    fun historyItemModel_userUploadedImageSupport() {
        val item = HistoryItemModel(
            type = "Gom ảnh",
            title = "Video tạo từ ảnh người dùng",
            date = "9 thg 10, 2026 • 18:30",
            imageUri = "content://media/external/images/media/12345"
        )
        val json = gson.toJson(item)
        val deserialized = gson.fromJson(json, HistoryItemModel::class.java)

        assertEquals("Gom ảnh", deserialized.type)
        assertEquals("Video tạo từ ảnh người dùng", deserialized.title)
        assertEquals("content://media/external/images/media/12345", deserialized.imageUri)
        assertNull(deserialized.imageUrl)
    }

    @Test
    fun musicCategory_separationFromTemplateCategory() {
        val musicCat1 = CategoryModel(code = "MUSIC_EDM", name = "EDM", groupCode = "MUSIC")
        assertTrue(musicCat1.isMusicCategory)
        assertEquals("EDM", musicCat1.displayName)

        val musicCat2 = CategoryModel(code = "MUSIC_CINEMATIC", name = "MUSIC_CINEMATIC")
        assertTrue(musicCat2.isMusicCategory)
        assertEquals("CINEMATIC", musicCat2.displayName)

        val templateCat = CategoryModel(code = "AI_DANCE", name = "AI Dance", groupCode = "HOME")
        assertFalse(templateCat.isMusicCategory)
        assertEquals("AI Dance", templateCat.displayName)

        val allList = listOf(CategoryModel.ALL, musicCat1, musicCat2, templateCat)
        val templateOnly = allList.filter { it.code == "ALL" || !it.isMusicCategory }
        val musicOnly = allList.filter { it.code == "ALL" || it.isMusicCategory }

        assertEquals(2, templateOnly.size) // ALL, AI_DANCE
        assertEquals(3, musicOnly.size) // ALL, MUSIC_EDM, MUSIC_CINEMATIC
    }

    @Test
    fun templateModel_isMusicDetection() {
        val visualTemplate = TemplateModel(
            id = "vid_1",
            title = "K-Pop Dance",
            category = "AI_DANCE",
            videoUrl = "https://example.com/dance.mp4"
        )
        assertFalse(visualTemplate.isMusic)

        val musicTemplate = TemplateModel(
            id = "mus_1",
            title = "Future Bass",
            category = "MUSIC_BASS",
            videoUrl = "https://example.com/audio.mp3"
        )
        assertTrue(musicTemplate.isMusic)
        assertEquals("https://example.com/audio.mp3", musicTemplate.safeAudioUrl)
    }
}