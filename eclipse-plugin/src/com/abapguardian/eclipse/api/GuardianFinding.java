package com.abapguardian.eclipse.api;

import java.util.List;

/**
 * Immutable client-side representation of a finding returned by the
 * ABAP Guardian gateway. Part of the plug-in's exported API.
 */
public final class GuardianFinding {

    private final String ruleId;
    private final String category;
    private final String severity;
    private final double confidence;
    private final String title;
    private final String explanation;
    private final String evidence;
    private final int startLine;
    private final int startColumn;
    private final int endLine;
    private final int endColumn;
    private final String recommendation;
    private final String suggestedCode;
    private final boolean requiresHumanReview;
    private final List<String> documentationReferences;

    public GuardianFinding(String ruleId, String category, String severity, double confidence,
                           String title, String explanation, String evidence,
                           int startLine, int startColumn, int endLine, int endColumn,
                           String recommendation, String suggestedCode,
                           boolean requiresHumanReview, List<String> documentationReferences) {
        this.ruleId = ruleId;
        this.category = category;
        this.severity = severity;
        this.confidence = confidence;
        this.title = title;
        this.explanation = explanation;
        this.evidence = evidence;
        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.recommendation = recommendation;
        this.suggestedCode = suggestedCode;
        this.requiresHumanReview = requiresHumanReview;
        this.documentationReferences = List.copyOf(documentationReferences);
    }

    public String getRuleId() { return ruleId; }
    public String getCategory() { return category; }
    public String getSeverity() { return severity; }
    public double getConfidence() { return confidence; }
    public String getTitle() { return title; }
    public String getExplanation() { return explanation; }
    public String getEvidence() { return evidence; }
    public int getStartLine() { return startLine; }
    public int getStartColumn() { return startColumn; }
    public int getEndLine() { return endLine; }
    public int getEndColumn() { return endColumn; }
    public String getRecommendation() { return recommendation; }
    public String getSuggestedCode() { return suggestedCode; }
    public boolean isRequiresHumanReview() { return requiresHumanReview; }
    public List<String> getDocumentationReferences() { return documentationReferences; }
}
