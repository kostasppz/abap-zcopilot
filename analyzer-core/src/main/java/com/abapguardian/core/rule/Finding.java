package com.abapguardian.core.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One analysis finding. Line and column numbers are 1-based and always come
 * from deterministic analysis of the supplied source.
 */
public final class Finding {

    private final String ruleId;
    private final RuleCategory category;
    private final RuleSeverity severity;
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
    private final SuggestedFix suggestedFix;

    private Finding(Builder b) {
        this.ruleId = Objects.requireNonNull(b.ruleId, "ruleId");
        this.category = Objects.requireNonNull(b.category, "category");
        this.severity = Objects.requireNonNull(b.severity, "severity");
        this.confidence = b.confidence;
        this.title = Objects.requireNonNull(b.title, "title");
        this.explanation = Objects.requireNonNull(b.explanation, "explanation");
        this.evidence = b.evidence == null ? "" : b.evidence;
        this.startLine = b.startLine;
        this.startColumn = b.startColumn;
        this.endLine = b.endLine;
        this.endColumn = b.endColumn;
        this.recommendation = b.recommendation == null ? "" : b.recommendation;
        this.suggestedCode = b.suggestedCode;
        this.requiresHumanReview = b.requiresHumanReview;
        this.documentationReferences = List.copyOf(b.documentationReferences);
        this.suggestedFix = b.suggestedFix;
    }

    public String getRuleId() {
        return ruleId;
    }

    public RuleCategory getCategory() {
        return category;
    }

    public RuleSeverity getSeverity() {
        return severity;
    }

    /** 0.0 .. 1.0 */
    public double getConfidence() {
        return confidence;
    }

    public String getTitle() {
        return title;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getEvidence() {
        return evidence;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getStartColumn() {
        return startColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public String getRecommendation() {
        return recommendation;
    }

    /** Suggested replacement code; may be null when no suggestion exists. */
    public String getSuggestedCode() {
        return suggestedCode;
    }

    public boolean isRequiresHumanReview() {
        return requiresHumanReview;
    }

    public List<String> getDocumentationReferences() {
        return documentationReferences;
    }

    /** Optional machine-applicable fix; may be null. */
    public SuggestedFix getSuggestedFix() {
        return suggestedFix;
    }

    /** Copy of this finding with a different severity (for configuration overrides). */
    public Finding withSeverity(RuleSeverity newSeverity) {
        Builder b = toBuilder();
        b.severity(newSeverity);
        return b.build();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.ruleId = ruleId;
        b.category = category;
        b.severity = severity;
        b.confidence = confidence;
        b.title = title;
        b.explanation = explanation;
        b.evidence = evidence;
        b.startLine = startLine;
        b.startColumn = startColumn;
        b.endLine = endLine;
        b.endColumn = endColumn;
        b.recommendation = recommendation;
        b.suggestedCode = suggestedCode;
        b.requiresHumanReview = requiresHumanReview;
        b.documentationReferences = new ArrayList<>(documentationReferences);
        b.suggestedFix = suggestedFix;
        return b;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String ruleId;
        private RuleCategory category;
        private RuleSeverity severity;
        private double confidence = 0.5;
        private String title;
        private String explanation;
        private String evidence;
        private int startLine = 1;
        private int startColumn = 1;
        private int endLine = 1;
        private int endColumn = 1;
        private String recommendation;
        private String suggestedCode;
        private boolean requiresHumanReview;
        private List<String> documentationReferences = new ArrayList<>();
        private SuggestedFix suggestedFix;

        public Builder ruleId(String v) {
            this.ruleId = v;
            return this;
        }

        public Builder category(RuleCategory v) {
            this.category = v;
            return this;
        }

        public Builder severity(RuleSeverity v) {
            this.severity = v;
            return this;
        }

        public Builder confidence(double v) {
            this.confidence = Math.max(0.0, Math.min(1.0, v));
            return this;
        }

        public Builder title(String v) {
            this.title = v;
            return this;
        }

        public Builder explanation(String v) {
            this.explanation = v;
            return this;
        }

        public Builder evidence(String v) {
            this.evidence = v;
            return this;
        }

        public Builder range(int startLine, int startColumn, int endLine, int endColumn) {
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.endLine = endLine;
            this.endColumn = endColumn;
            return this;
        }

        public Builder recommendation(String v) {
            this.recommendation = v;
            return this;
        }

        public Builder suggestedCode(String v) {
            this.suggestedCode = v;
            return this;
        }

        public Builder requiresHumanReview(boolean v) {
            this.requiresHumanReview = v;
            return this;
        }

        public Builder addDocumentationReference(String v) {
            this.documentationReferences.add(v);
            return this;
        }

        public Builder documentationReferences(List<String> v) {
            this.documentationReferences = new ArrayList<>(v == null ? Collections.emptyList() : v);
            return this;
        }

        public Builder suggestedFix(SuggestedFix v) {
            this.suggestedFix = v;
            return this;
        }

        public Finding build() {
            return new Finding(this);
        }
    }
}
