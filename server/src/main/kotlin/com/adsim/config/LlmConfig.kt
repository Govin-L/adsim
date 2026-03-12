package com.adsim.config

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class LlmConfig {

    @Bean
    fun chatModel(
        @Value("\${adsim.llm.api-key}") apiKey: String,
        @Value("\${adsim.llm.base-url}") baseUrl: String,
        @Value("\${adsim.llm.model}") model: String
    ): ChatModel {
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
