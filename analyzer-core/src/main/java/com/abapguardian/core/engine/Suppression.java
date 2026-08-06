package com.abapguardian.core.engine;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A suppression pseudo comment:
 *
 * <pre>"#EC ABAP_GUARDIAN: RULE_ID reason="Approved protected audit log"</pre>
 *
 * A suppression is only valid when a non-empty reason is present.
 */
public record Suppression(String ruleId, String reason, int line, boolean valid) {

    private static final Pattern PATTERN = Pattern.compile(
            "\"#EC\\s+ABAP_GUARDIAN:\\s*(?<rule>[A-Za-z0-9_]+)(?<rest>.*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern REASON = Pattern.compile(
            "reason\\s*=\\s*\"(?<reason>[^\"]*)\"", Pattern.CASE_INSENSITIVE);

    /** Parses a pseudo-comment token text; empty when not a Guardian suppression. */
    public static Optional<Suppression> parse(String pseudoCommentText, int line) {
        if (pseudoCommentText == null) {
            return Optional.empty();
        }
        Matcher m = PATTERN.matcher(pseudoCommentText.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        String ruleId = m.group("rule").toUpperCase(Locale.ROOT);
        String rest = m.group("rest") == null ? "" : m.group("rest");
        Matcher r = REASON.matcher(rest);
        String reason = "";
        if (r.find()) {
            reason = r.group("reason").trim();
        }
        boolean valid = !reason.isEmpty();
        return Optional.of(new Suppression(ruleId, reason, line, valid));
    }

    public boolean suppresses(String findingRuleId, int findingStartLine, int findingEndLine) {
        if (!valid) {
            return false;
        }
        if (!ruleId.equalsIgnoreCase(findingRuleId)) {
            return false;
        }
        return line >= findingStartLine - 1 && line <= findingEndLine + 1;
    }
}
