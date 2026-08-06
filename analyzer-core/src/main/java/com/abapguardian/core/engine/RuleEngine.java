package com.abapguardian.core.engine;

import com.abapguardian.core.config.RuleConfiguration;
import com.abapguardian.core.lexer.AbapToken;
import com.abapguardian.core.lexer.AbapTokenizer;
import com.abapguardian.core.lexer.TokenType;
import com.abapguardian.core.model.AbapParser;
import com.abapguardian.core.model.AbapSource;
import com.abapguardian.core.rule.AbapRule;
import com.abapguardian.core.rule.AnalysisContext;
import com.abapguardian.core.rule.Finding;
import com.abapguardian.core.rule.RuleSeverity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Configurable rule engine: parses the source, runs all enabled rules, applies
 * severity overrides, confidence thresholds and suppression pseudo comments.
 */
public final class RuleEngine {

    private final List<AbapRule> rules;
    private final RuleConfiguration configuration;
    private final AbapParser parser = new AbapParser();
    private final AbapTokenizer tokenizer = new AbapTokenizer();

    public RuleEngine(List<AbapRule> rules, RuleConfiguration configuration) {
        this.rules = List.copyOf(rules);
        this.configuration = configuration;
    }

    public AnalysisResult analyze(String source, String objectName, String objectType) {
        AbapSource parsed = parser.parse(source);
        AnalysisContext context = new AnalysisContext(parsed, configuration, objectName, objectType);

        List<Suppression> suppressions = collectSuppressions(source);
        List<Finding> findings = new ArrayList<>();
        List<Finding> suppressed = new ArrayList<>();

        for (AbapRule rule : rules) {
            RuleConfiguration.RuleSettings settings = configuration.settingsFor(rule.getRuleId());
            if (!settings.isEnabled()) {
                continue;
            }
            double threshold = settings.getConfidenceThreshold()
                    .orElse(configuration.getDefaultConfidenceThreshold());
            for (Finding finding : rule.analyze(context)) {
                Optional<RuleSeverity> override = settings.getSeverityOverride();
                Finding effective = override.map(finding::withSeverity).orElse(finding);
                if (effective.getConfidence() < threshold) {
                    continue;
                }
                if (isSuppressed(effective, suppressions)) {
                    suppressed.add(effective);
                } else {
                    findings.add(effective);
                }
            }
        }
        findings.sort(Comparator.comparingInt(Finding::getStartLine)
                .thenComparingInt(Finding::getStartColumn)
                .thenComparing(Finding::getRuleId));
        return new AnalysisResult(objectName, objectType, findings, suppressed);
    }

    private boolean isSuppressed(Finding finding, List<Suppression> suppressions) {
        for (Suppression s : suppressions) {
            if (s.suppresses(finding.getRuleId(), finding.getStartLine(), finding.getEndLine())) {
                return true;
            }
        }
        return false;
    }

    private List<Suppression> collectSuppressions(String source) {
        List<Suppression> result = new ArrayList<>();
        for (AbapToken token : tokenizer.tokenize(source)) {
            if (token.getType() == TokenType.PSEUDO_COMMENT) {
                Suppression.parse(token.getText(), token.getLine()).ifPresent(result::add);
            }
        }
        return result;
    }
}
