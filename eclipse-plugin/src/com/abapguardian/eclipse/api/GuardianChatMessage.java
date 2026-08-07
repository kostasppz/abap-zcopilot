package com.abapguardian.eclipse.api;

/** One in-memory Copilot conversation turn. Conversation text is not persisted. */
public record GuardianChatMessage(String role, String content) {
}
