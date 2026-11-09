# Mika tool status

The local Gemma build currently uses chat-only inference. The earlier Android tool executor and safety gate are retained as scaffolding, but are not connected to LiteRT-LM conversations.

This is intentional: phone actions need a confirmation-aware integration before they can be exposed to an on-device model. Do not register calling, messaging, deleting, purchasing, payments, or other sensitive actions as automatic tools.

Current active capabilities:
- Local Gemma text generation
- One-shot speech recognition
- Android TextToSpeech

Not connected yet:
- App launching and alarm tools
- Calls or messaging
- Accessibility action loop
- Notification summarization/reply actions
- Wake-word detection
