package com.abapguardian.core.config;

import com.abapguardian.core.rule.RuleSeverity;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Rule configuration, loadable from YAML.
 *
 * <p>Supports per-rule enabled state, severity override, confidence threshold
 * and allowed exceptions, plus global lists of sensitive tables and fields,
 * approved destinations and naming conventions.
 */
public final class RuleConfiguration {

    /** Per-rule settings keyed by rule id. */
    public static final class RuleSettings {
        private boolean enabled = true;
        private RuleSeverity severityOverride;
        private Double confidenceThreshold;
        private List<String> allowedExceptions = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public Optional<RuleSeverity> getSeverityOverride() {
            return Optional.ofNullable(severityOverride);
        }

        public Optional<Double> getConfidenceThreshold() {
            return Optional.ofNullable(confidenceThreshold);
        }

        public List<String> getAllowedExceptions() {
            return Collections.unmodifiableList(allowedExceptions);
        }
    }

    private final Map<String, RuleSettings> ruleSettings = new HashMap<>();
    private final Set<String> sensitiveTables = new HashSet<>();
    private final Set<String> sensitiveFields = new HashSet<>();
    private final Set<String> approvedDestinations = new HashSet<>();
    private final Map<String, String> namingConventions = new HashMap<>();
    private double defaultConfidenceThreshold = 0.0;
    private int excessiveFieldThreshold = 3;

    public static RuleConfiguration defaults() {
        RuleConfiguration c = new RuleConfiguration();
        c.sensitiveFields.addAll(List.of(
                "PERNR", "NACHN", "VORNA", "GBDAT", "STRAS", "ORT01", "BANKN",
                "IBAN", "USRID", "EMAIL", "PHONE"));
        c.sensitiveTables.addAll(List.of("PA0002", "PA0006", "PA0009"));
        return c;
    }

    /** Load configuration from a YAML document, merged over the defaults. */
    @SuppressWarnings("unchecked")
    public static RuleConfiguration fromYaml(InputStream in) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object root = yaml.load(in);
        RuleConfiguration c = defaults();
        if (!(root instanceof Map)) {
            return c;
        }
        Map<String, Object> map = (Map<String, Object>) root;
        Object rules = map.get("rules");
        if (rules instanceof Map<?, ?> ruleMap) {
            for (Map.Entry<?, ?> e : ruleMap.entrySet()) {
                String ruleId = String.valueOf(e.getKey()).toUpperCase(Locale.ROOT);
                RuleSettings settings = new RuleSettings();
                if (e.getValue() instanceof Map<?, ?> s) {
                    Object enabled = s.get("enabled");
                    if (enabled instanceof Boolean b) {
                        settings.enabled = b;
                    }
                    Object severity = s.get("severity");
                    if (severity != null) {
                        settings.severityOverride =
                                RuleSeverity.valueOf(String.valueOf(severity).toUpperCase(Locale.ROOT));
                    }
                    Object threshold = s.get("confidenceThreshold");
                    if (threshold instanceof Number n) {
                        settings.confidenceThreshold = n.doubleValue();
                    }
                    Object exceptions = s.get("allowedExceptions");
                    if (exceptions instanceof List<?> list) {
                        for (Object o : list) {
                            settings.allowedExceptions.add(String.valueOf(o));
                        }
                    }
                }
                c.ruleSettings.put(ruleId, settings);
            }
        }
        mergeUppercaseList(map.get("sensitiveTables"), c.sensitiveTables);
        mergeUppercaseList(map.get("sensitiveFields"), c.sensitiveFields);
        mergeUppercaseList(map.get("approvedDestinations"), c.approvedDestinations);
        Object naming = map.get("namingConventions");
        if (naming instanceof Map<?, ?> nm) {
            for (Map.Entry<?, ?> e : nm.entrySet()) {
                c.namingConventions.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        Object defaultThreshold = map.get("defaultConfidenceThreshold");
        if (defaultThreshold instanceof Number n) {
            c.defaultConfidenceThreshold = n.doubleValue();
        }
        Object excessive = map.get("excessiveSensitiveFieldThreshold");
        if (excessive instanceof Number n) {
            c.excessiveFieldThreshold = n.intValue();
        }
        return c;
    }

    private static void mergeUppercaseList(Object value, Set<String> target) {
        if (value instanceof List<?> list) {
            target.clear();
            for (Object o : list) {
                target.add(String.valueOf(o).toUpperCase(Locale.ROOT));
            }
        }
    }

    public RuleSettings settingsFor(String ruleId) {
        return ruleSettings.getOrDefault(ruleId.toUpperCase(Locale.ROOT), new RuleSettings());
    }

    public boolean isRuleEnabled(String ruleId) {
        return settingsFor(ruleId).isEnabled();
    }

    public Set<String> getSensitiveTables() {
        return Collections.unmodifiableSet(sensitiveTables);
    }

    public Set<String> getSensitiveFields() {
        return Collections.unmodifiableSet(sensitiveFields);
    }

    public Set<String> getApprovedDestinations() {
        return Collections.unmodifiableSet(approvedDestinations);
    }

    public Map<String, String> getNamingConventions() {
        return Collections.unmodifiableMap(namingConventions);
    }

    public double getDefaultConfidenceThreshold() {
        return defaultConfidenceThreshold;
    }

    public int getExcessiveFieldThreshold() {
        return excessiveFieldThreshold;
    }

    /** True when the (uppercased) identifier is or contains a configured sensitive field name. */
    public boolean isSensitiveIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        String upper = identifier.toUpperCase(Locale.ROOT);
        if (sensitiveFields.contains(upper) || sensitiveTables.contains(upper)) {
            return true;
        }
        // Structure component access such as ls_pa0002-pernr
        int dash = upper.lastIndexOf('-');
        if (dash >= 0 && dash < upper.length() - 1) {
            String component = upper.substring(dash + 1);
            if (sensitiveFields.contains(component)) {
                return true;
            }
        }
        // Variable named after a sensitive field, e.g. lv_pernr
        for (String field : sensitiveFields) {
            if (upper.endsWith("_" + field)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSensitiveTable(String tableName) {
        return tableName != null && sensitiveTables.contains(tableName.toUpperCase(Locale.ROOT));
    }
}
