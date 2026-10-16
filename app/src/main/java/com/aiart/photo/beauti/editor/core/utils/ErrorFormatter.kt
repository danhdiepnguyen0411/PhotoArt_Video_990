package com.aiart.photo.beauti.editor.core.utils

import android.content.Context
import com.aiart.photo.beauti.editor.R

object ErrorFormatter {

    /**
     * Parses and converts any raw backend/SDK error message or throwable
     * into a polite, user-friendly, localized message.
     */
    fun formatVideoErrorMessage(context: Context, rawError: String?): String {
        if (rawError.isNullOrBlank()) {
            return context.getString(R.string.error_create_video_general)
        }

        val lower = rawError.lowercase()

        // 1. Check for face detection / invalid face failure
        // Server typically returns:
        // "Bad Request 400 Bad Request body={'status': {'code': 400, 'message': 'No face detected in the image'}}"
        // or mentions face / no face / no human / bad request on video generation
        val isFaceError = lower.contains("face") ||
                lower.contains("khuôn mặt") ||
                lower.contains("mặt người") ||
                lower.contains("human") ||
                lower.contains("person") ||
                lower.contains("noface") ||
                lower.contains("no_face") ||
                lower.contains("not found face") ||
                lower.contains("face not found") ||
                lower.contains("400") ||
                lower.contains("bad request")

        if (isFaceError) {
            return context.getString(R.string.error_no_face_detected)
        }

        // 2. Check for network / connection issues
        val isNetworkError = lower.contains("connect") ||
                lower.contains("network") ||
                lower.contains("unknownhost") ||
                lower.contains("socket") ||
                lower.contains("offline") ||
                lower.contains("unreachable") ||
                lower.contains("no internet")

        if (isNetworkError) {
            return context.getString(R.string.error_network_connection)
        }

        // 3. Check for timeout (polling or request timeout)
        val isTimeout = lower.contains("timeout") || lower.contains("timed out") || lower.contains("408")
        if (isTimeout) {
            return context.getString(R.string.error_video_generation_timeout)
        }

        // 4. Check for server errors (500, 502, 503, 504)
        val isServerError = lower.contains("500") ||
                lower.contains("502") ||
                lower.contains("503") ||
                lower.contains("504") ||
                lower.contains("internal server") ||
                lower.contains("server error")

        if (isServerError) {
            return context.getString(R.string.error_server_busy)
        }

        // 5. Try extracting inner JSON/dict message if present
        val extracted = extractServerMessage(rawError)
        if (!extracted.isNullOrBlank()) {
            val extractedLower = extracted.lowercase()
            if (extractedLower.contains("face") ||
                extractedLower.contains("khuôn mặt") ||
                extractedLower.contains("mặt người") ||
                extractedLower.contains("human") ||
                extractedLower.contains("person") ||
                extractedLower.contains("noface")
            ) {
                return context.getString(R.string.error_no_face_detected)
            }
        }

        // Default fallback: Never show raw code/JSON
        return context.getString(R.string.error_create_video_general)
    }

    fun formatVideoErrorMessage(context: Context, throwable: Throwable?): String {
        return formatVideoErrorMessage(context, throwable?.message)
    }

    private fun extractServerMessage(rawError: String): String? {
        val regex = Regex(
            """['"](?:message|msg|error|detail|description)['"]\s*:\s*['"]([^'"]+)['"]""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(rawError)
        return match?.groupValues?.getOrNull(1)
    }
}
