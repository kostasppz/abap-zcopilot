package com.abapguardian.eclipse.api;

import java.util.List;

/** Result of one gateway analysis call. Part of the exported API. */
public final class GuardianAnalysisResult {

    private final String objectName;
    private final String objectType;
    private final List<GuardianFinding> findings;
    private final List<GuardianFinding> suppressedFindings;
    private final boolean aiEnhanced;

    public GuardianAnalysisResult(String objectName, String objectType,
                                  List<GuardianFinding> findings,
                                  List<GuardianFinding> suppressedFindings,
                                  boolean aiEnhanced) {
        this.objectName = objectName;
        this.objectType = objectType;
        this.findings = List.copyOf(findings);
        this.suppressedFindings = List.copyOf(suppressedFindings);
        this.aiEnhanced = aiEnhanced;
    }

    public String getObjectName() { return objectName; }
    public String getObjectType() { return objectType; }
    public List<GuardianFinding> getFindings() { return findings; }
    public List<GuardianFinding> getSuppressedFindings() { return suppressedFindings; }
    public boolean isAiEnhanced() { return aiEnhanced; }
}
