package com.adsim.config

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder
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

    companion object {
        // Use HTTP/1.1 to avoid HTTP/2 concurrent stream limits under high concurrency
        private fun http11ClientBuilder() = JdkHttpClientBuilder()
            .httpClientBuilder(
                java.net.http.HttpClient.newBuilder()
                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
            )
    }

    fun resolve(request: HttpServletRequest): ChatModel {
        val apiKey = request.getHeader("X-LLM-Api-Key") ?: return defaultModel
        val baseUrl = request.getHeader("X-LLM-Base-Url") ?: "https://api.openai.com/v1"
        val model = request.getHeader("X-LLM-Model") ?: "gpt-4o-mini"

        return OpenAiChatModel.builder()
            .httpClientBuilder(http11ClientBuilder())
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(model)
            .temperature(0.7)
            .responseFormat("json_object")
            .timeout(Duration.ofSeconds(120))
            .build()
    }
}
