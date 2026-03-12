package com.adsim.config

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Resolves ChatModel per request.
 * If X-LLM-* headers are present, creates a request-scoped model.
 * Otherwise falls back to the default bean.
 * NEVER logs or persists any credential information.
 */
@Component
class LlmRequestConfig(
    private val defaultModel: ChatModel
) {

    fun resolve(request: HttpServletRequest): ChatModel {
        val apiKey = request.getHeader("X-LLM-Api-Key") ?: return defaultModel
        val baseUrl = request.getHeader("X-LLM-Base-Url") ?: "https://api.openai.com/v1"
        val model = request.getHeader("X-LLM-Model") ?: "gpt-4o-mini"

        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(model)
            .temperature(0.7)
            .responseFormat("json_object")
            .timeout(Duration.ofSeconds(60))
            .build()
    }
}
