package com.abapguardian.core.rule;

/** Severity levels of findings, in ascending order. */
public enum RuleSeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public boolean isAtLeast(RuleSeverity other) {
        return ordinal() >= other.ordinal();
    }
}
