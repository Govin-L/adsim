package com.adsim.config

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder
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
            .httpClientBuilder(
                JdkHttpClientBuilder().httpClientBuilder(
                    java.net.http.HttpClient.newBuilder()
                        .version(java.net.http.HttpClient.Version.HTTP_1_1)
                )
            )
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(model)
            .temperature(0.7)
            .responseFormat("json_object")
            .timeout(Duration.ofSeconds(120))
            .build()
    }
}
