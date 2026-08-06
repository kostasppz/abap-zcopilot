package com.abapguardian.core.rules;

import com.abapguardian.core.rule.AbapRule;
import com.abapguardian.core.rule.RuleCategory;
import com.abapguardian.core.rule.RuleSeverity;

/** Convenience base class carrying rule metadata. */
public abstract class AbstractRule implements AbapRule {

    private final String ruleId;
    private final RuleCategory category;
    private final RuleSeverity defaultSeverity;

    protected AbstractRule(String ruleId, RuleCategory category, RuleSeverity defaultSeverity) {
        this.ruleId = ruleId;
        this.category = category;
        this.defaultSeverity = defaultSeverity;
    }

    @Override
    public final String getRuleId() {
        return ruleId;
    }

    @Override
    public final RuleCategory getCategory() {
        return category;
    }

    @Override
    public final RuleSeverity getDefaultSeverity() {
        return defaultSeverity;
    }
}
