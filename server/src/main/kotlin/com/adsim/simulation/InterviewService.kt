package com.adsim.simulation

import com.adsim.api.dto.InterviewRequest
import com.adsim.api.dto.InterviewResponse
import com.adsim.model.Agent
import com.adsim.model.Simulation
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class InterviewService {
    private val logger = LoggerFactory.getLogger(InterviewService::class.java)
    private val conversations = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    companion object {
        private const val MAX_CONVERSATIONS = 50
        private const val MAX_HISTORY_SIZE = 21 // system prompt + 10 exchanges (20 messages)
    }

    fun chat(simulation: Simulation, agent: Agent, request: InterviewRequest, chatModel: ChatModel): InterviewResponse {
        val conversationId = request.conversationId ?: UUID.randomUUID().toString()
        val history = conversations.getOrPut(conversationId) { mutableListOf() }

        if (history.isEmpty()) {
            history.add(SystemMessage.from(buildSystemPrompt(simulation, agent)))
        }

        history.add(UserMessage.from(request.message))

        // Cap history to prevent unbounded growth: keep system prompt + last N exchanges
        while (history.size > MAX_HISTORY_SIZE) {
            // Remove oldest non-system message (system prompt is at index 0)
            if (history.size > 1) history.removeAt(1) else break
        }

        logger.info("Interview agent: {}, conversationId: {}", agent.id, conversationId)

        val response = chatModel.chat(
            ChatRequest.builder()
                .messages(history)
                .build()
        )

        val reply = response.aiMessage().text()
        history.add(AiMessage.from(reply))

        // Evict oldest conversation if over capacity
        if (conversations.size > MAX_CONVERSATIONS) {
            val oldest = conversations.keys.firstOrNull()
            if (oldest != null) conversations.remove(oldest)
        }

        return InterviewResponse(reply, conversationId)
    }

    private fun buildSystemPrompt(simulation: Simulation, agent: Agent): String {
        val persona = agent.persona
        val decisions = agent.decisions
        val input = simulation.input
        val campaignState = agent.campaignState
        val placementSummary = if (agent.placementOutcomes.isNotEmpty()) {
            agent.placementOutcomes.joinToString("\n") { placement ->
                val deliveryHint = placement.exposureEvent.deliveryContext.let { context ->
                    buildString {
                        append("source=${context.source}")
                        append(", frequency=${context.frequency}")
                        context.intentLevel?.let { append(", intent=${(it * 100).toInt()}%") }
                    }
                }
                "- ${placement.platform}/${placement.placementType}: attention=${placement.attention.passed}, click=${placement.click.passed}, conversion=${placement.conversion.passed}; $deliveryHint"
            }
        } else {
            "- No placement-level decisions recorded"
        }

        return """
You are ${persona.name}, a ${persona.age}-year-old ${persona.gender} from a tier-${persona.cityTier} city.
Your income level is ${persona.income}. Your interests include: ${persona.interests.joinToString(", ")}.
You use ${input.adPlacements.firstOrNull()?.platform ?: "this platform"} about ${persona.platformBehavior.dailyUsageMinutes} minutes per day.
Your price sensitivity is ${persona.consumptionHabits.priceSensitivity}, decision speed is ${persona.consumptionHabits.decisionSpeed}.

You were shown an ad for: ${input.product.name} (${input.product.category}, ¥${input.product.price})
Ad creative: ${input.adPlacements.firstOrNull()?.creativeDescription ?: "an advertisement"}

Your reactions were:
- Attention: ${if (decisions?.attention?.passed == true) "Noticed" else "Did not notice"} — "${decisions?.attention?.reasoning ?: "N/A"}"
- Click: ${if (decisions?.click?.passed == true) "Clicked" else "Did not click"} — "${decisions?.click?.reasoning ?: "N/A"}"
- Conversion: ${if (decisions?.conversion?.passed == true) "Purchased" else "Did not purchase"} — "${decisions?.conversion?.reasoning ?: "N/A"}"

Campaign state summary:
- placements seen: ${campaignState.placementsSeen}
- noticed count: ${campaignState.noticedCount}
- clicked count: ${campaignState.clickedCount}
- converted count: ${campaignState.convertedCount}
- fatigue score: ${campaignState.fatigueScore}
- campaign brand familiarity: ${campaignState.brandFamiliarity.label}

Placement breakdown:
$placementSummary

The user (a marketer) is now interviewing you to understand your decision.
Stay in character. Answer from YOUR perspective as this person.
Be honest and specific. Reference your actual persona attributes when explaining decisions.
        """.trimIndent()
    }
}
