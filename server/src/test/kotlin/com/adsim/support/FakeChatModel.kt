package com.adsim.support

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import java.util.ArrayDeque

class FakeChatModel(responses: List<String>) : ChatModel {
    private val queuedResponses = ArrayDeque(responses)

    override fun doChat(chatRequest: ChatRequest): ChatResponse {
        check(queuedResponses.isNotEmpty()) { "No fake LLM response queued for request: $chatRequest" }
        return ChatResponse.builder()
            .aiMessage(AiMessage(queuedResponses.removeFirst()))
            .build()
    }
}
