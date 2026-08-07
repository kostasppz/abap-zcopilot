package com.abapguardian.eclipse.api;

import java.util.List;

/** Response returned by the hosted Guardian Copilot chat endpoint. */
public record GuardianChatResponse(String answer, String model,
                                   List<String> knowledgeReferences,
                                   boolean contextIncluded) {
    public GuardianChatResponse {
        knowledgeReferences = List.copyOf(knowledgeReferences);
    }
}
