package com.abapguardian.core.rule;

import com.abapguardian.core.config.RuleConfiguration;
import com.abapguardian.core.model.AbapSource;

import java.util.Objects;

/** Everything a rule needs to analyze one ABAP source unit. */
public final class AnalysisContext {

    private final AbapSource source;
    private final RuleConfiguration configuration;
    private final String objectName;
    private final String objectType;

    public AnalysisContext(AbapSource source, RuleConfiguration configuration,
                           String objectName, String objectType) {
        this.source = Objects.requireNonNull(source, "source");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.objectName = objectName == null ? "" : objectName;
        this.objectType = objectType == null ? "" : objectType;
    }

    public AbapSource getSource() {
        return source;
    }

    public RuleConfiguration getConfiguration() {
        return configuration;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getObjectType() {
        return objectType;
    }
}
