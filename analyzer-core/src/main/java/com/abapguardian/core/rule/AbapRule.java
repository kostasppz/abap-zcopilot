package com.abapguardian.core.rule;

import java.util.List;

/** A deterministic ABAP analysis rule. */
public interface AbapRule {

    String getRuleId();

    RuleCategory getCategory();

    RuleSeverity getDefaultSeverity();

    List<Finding> analyze(AnalysisContext context);
}
