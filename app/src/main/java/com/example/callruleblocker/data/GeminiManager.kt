package com.example.callruleblocker.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GeminiManager {
    private const val API_KEY = "AIzaSyCN4fFi1IDkR2BYmjybqn0bzuu598i-A9U" // From ShynaApplication
    
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = API_KEY
    )

    suspend fun generateSummary(audioFile: File): String = withContext(Dispatchers.IO) {
        try {
            val audioBytes = audioFile.readBytes()
            val response = model.generateContent(content {
                blob("audio/mpeg", audioBytes)
                text("Summarize this call recording. Identify the caller, the main topic, and any action items.")
            })
            response.text ?: "No summary generated."
        } catch (e: Exception) {
            "Summary failed: ${e.message}"
        }
    }

    suspend fun askAi(query: String): String = withContext(Dispatchers.IO) {
        try {
            val response = model.generateContent(content {
                text(query)
            })
            response.text ?: "I'm sorry, I couldn't process that."
        } catch (e: Exception) {
            "AI Error: ${e.message}"
        }
    }
}
