package com.abapguardian.core.rules;

import com.abapguardian.core.rule.AbapRule;
import com.abapguardian.core.rules.cleancode.CleanCodeRules;
import com.abapguardian.core.rules.performance.PerformanceRules;
import com.abapguardian.core.rules.privacy.PrivacyRules;
import com.abapguardian.core.rules.s4hana.S4HanaRules;
import com.abapguardian.core.rules.security.SecurityRules;

import java.util.ArrayList;
import java.util.List;

/** Registry of all built-in deterministic rules. */
public final class RuleRegistry {

    private RuleRegistry() {
    }

    public static List<AbapRule> allRules() {
        List<AbapRule> rules = new ArrayList<>();
        rules.addAll(PerformanceRules.all());
        rules.addAll(SecurityRules.all());
        rules.addAll(S4HanaRules.all());
        rules.addAll(CleanCodeRules.all());
        rules.addAll(PrivacyRules.all());
        return List.copyOf(rules);
    }
}
