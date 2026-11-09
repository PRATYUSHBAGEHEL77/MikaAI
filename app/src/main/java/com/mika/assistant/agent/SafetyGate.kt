package com.mika.assistant.agent

class SafetyGate {
    private val pending = linkedMapOf<String, PendingAction>()
    private val riskyTools = setOf("call_contact", "send_sms", "delete_file", "make_payment", "purchase")

    fun requiresConfirmation(tool: String): Boolean = tool in riskyTools

    fun create(tool: String, id: String, input: String, description: String): PendingAction {
        return PendingAction(id, tool, input, description).also { pending[id] = it }
    }

    fun consume(id: String): PendingAction? = pending.remove(id)
    fun cancel(id: String) { pending.remove(id) }
    fun clear() { pending.clear() }
}
