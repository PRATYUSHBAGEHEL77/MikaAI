package com.mika.assistant.agent

data class ChatMessage(val speaker: String, val text: String, val fromUser: Boolean)
data class PendingAction(val id: String, val tool: String, val inputJson: String, val description: String)
sealed interface AgentEvent {
    data class Text(val text: String) : AgentEvent
    data class Status(val text: String) : AgentEvent
    data class Confirmation(val action: PendingAction) : AgentEvent
    data class Error(val message: String) : AgentEvent
    data object Finished : AgentEvent
}
