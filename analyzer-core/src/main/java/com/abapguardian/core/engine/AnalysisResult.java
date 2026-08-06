package com.abapguardian.core.engine;

import com.abapguardian.core.rule.Finding;

import java.util.List;

/** Result of a rule-engine run. */
public record AnalysisResult(
        String objectName,
        String objectType,
        List<Finding> findings,
        List<Finding> suppressedFindings) {

    public AnalysisResult {
        findings = List.copyOf(findings);
        suppressedFindings = List.copyOf(suppressedFindings);
    }
}
