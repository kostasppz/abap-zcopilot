package com.abapguardian.core.rules;

import com.abapguardian.core.lexer.AbapToken;
import com.abapguardian.core.model.AbapBlock;
import com.abapguardian.core.model.AbapStatement;
import com.abapguardian.core.model.BlockType;
import com.abapguardian.core.rule.Finding;
import com.abapguardian.core.rule.RuleCategory;
import com.abapguardian.core.rule.RuleSeverity;

import java.util.List;
import java.util.Locale;

/** Shared helpers for deterministic rules. */
public final class RuleSupport {

    private RuleSupport() {
    }

    /** Builder pre-populated with the statement's range and evidence text. */
    public static Finding.Builder findingAt(AbapStatement st, String ruleId, RuleCategory category,
                                            RuleSeverity severity) {
        return Finding.builder()
                .ruleId(ruleId)
                .category(category)
                .severity(severity)
                .evidence(truncate(st.toSourceText(), 240))
                .range(st.getStartLine(), st.getStartColumn(), st.getEndLine(), st.getEndColumn());
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    /**
     * True when the needle occurs in the statement's non-literal code tokens
     * (identifiers/keywords). Literal and comment content is ignored, so text
     * inside strings can never match.
     */
    public static boolean codeContains(AbapStatement st, String upperNeedle) {
        for (AbapToken t : st.wordTokens()) {
            if (t.getUpperText().contains(upperNeedle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the statement is {@code CALL FUNCTION '<name>'} and the function
     * name literal (the token directly after FUNCTION) contains the needle.
     * Literals elsewhere in the statement never match.
     */
    public static boolean callsFunction(AbapStatement st, String upperNeedle) {
        if (!"CALL FUNCTION".equals(st.getLeadingWords(2))) {
            return false;
        }
        AbapToken name = st.tokenAfterWord("FUNCTION");
        return name != null && name.getUpperText().contains(upperNeedle);
    }

    /** True when the statement is nested inside any loop construct. */
    public static boolean isInLoop(AbapStatement st) {
        AbapBlock b = st.getBlock();
        if (b == null) {
            return false;
        }
        // The opening statement of a loop is not "inside" the loop it opens,
        // but is inside any outer loop.
        if (b.getOpeningStatement() == st) {
            b = b.getParent();
        }
        return b != null && b.isInsideLoop();
    }

    /** True when the statement is a database SELECT (not internal-table constructs). */
    public static boolean isSelect(AbapStatement st) {
        return "SELECT".equals(st.getFirstKeyword());
    }

    /** Uppercase name of the table after FROM, or null. */
    public static String selectTable(AbapStatement st) {
        AbapToken from = st.wordAfter("FROM");
        return from == null ? null : from.getUpperText();
    }

    /** True when a database-modifying statement targets a DB table (heuristic on target name). */
    public static boolean isDatabaseChange(AbapStatement st) {
        String kw = st.getFirstKeyword();
        switch (kw) {
            case "UPDATE" -> {
                return true;
            }
            case "INSERT" -> {
                // INSERT itab / INSERT ... INTO TABLE itab are internal-table operations.
                if (st.containsPhrase("INTO", "TABLE") || st.containsWord("INDEX")
                        || st.containsPhrase("INITIAL", "LINE") || st.containsWord("ASSIGNING")
                        || st.containsWord("REFERENCE")) {
                    return false;
                }
                return looksLikeDbTarget(firstTargetWord(st));
            }
            case "MODIFY" -> {
                if (st.containsWord("INDEX") || st.containsWord("TRANSPORTING")
                        || st.containsPhrase("MODIFY", "TABLE") || st.containsWord("SCREEN")
                        || st.containsWord("LINE")) {
                    return false;
                }
                return looksLikeDbTarget(firstTargetWord(st));
            }
            case "DELETE" -> {
                if (st.containsWord("INDEX") || st.containsPhrase("ADJACENT", "DUPLICATES")
                        || st.containsWord("WHERE") && !looksLikeDbTarget(firstTargetWord(st))) {
                    return false;
                }
                if (st.containsPhrase("DELETE", "DATASET") || st.containsPhrase("DELETE", "REPORT")) {
                    return false;
                }
                return looksLikeDbTarget(firstTargetWord(st));
            }
            default -> {
                return false;
            }
        }
    }

    private static String firstTargetWord(AbapStatement st) {
        List<AbapToken> words = st.wordTokens();
        if (words.size() < 2) {
            return null;
        }
        String second = words.get(1).getUpperText();
        if ("FROM".equals(second) || "INTO".equals(second) || "TABLE".equals(second)) {
            return words.size() >= 3 ? words.get(2).getUpperText() : null;
        }
        return second;
    }

    /**
     * Heuristic: internal tables conventionally start with LT_/GT_/IT_/MT_ or
     * contain a dash (structure component); DB tables are dictionary names.
     */
    public static boolean looksLikeDbTarget(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String n = name.toUpperCase(Locale.ROOT);
        if (n.contains("-") || n.startsWith("<")) {
            return false;
        }
        return !(n.startsWith("LT_") || n.startsWith("GT_") || n.startsWith("IT_")
                || n.startsWith("MT_") || n.startsWith("LS_") || n.startsWith("GS_")
                || n.startsWith("LV_") || n.startsWith("GV_") || n.startsWith("MV_")
                || n.startsWith("LO_") || n.startsWith("GO_"));
    }

    /** True when any WORD token references a sensitive identifier per configuration. */
    public static boolean referencesSensitiveData(AbapStatement st,
                                                  com.abapguardian.core.config.RuleConfiguration config) {
        for (AbapToken t : st.wordTokens()) {
            if (config.isSensitiveIdentifier(t.getText())) {
                return true;
            }
        }
        // string templates may embed sensitive fields: |{ ls_p-pernr }|
        for (AbapToken t : st.literalTokens()) {
            if (t.getType() == com.abapguardian.core.lexer.TokenType.STRING_TEMPLATE) {
                String upper = t.getUpperText();
                for (String field : config.getSensitiveFields()) {
                    if (upper.contains(field)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** True when the statement contains a dynamic clause such as FROM (var) or WHERE (var). */
    public static boolean hasDynamicSqlPart(AbapStatement st) {
        List<AbapToken> tokens = st.getTokens();
        for (int i = 0; i < tokens.size() - 2; i++) {
            AbapToken t = tokens.get(i);
            if (t.isWord()) {
                String w = t.getUpperText();
                if (("FROM".equals(w) || "WHERE".equals(w))
                        && "(".equals(tokens.get(i + 1).getText())
                        && tokens.get(i + 2).isWord()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Nearest enclosing block of the given type, or null. */
    public static AbapBlock enclosing(AbapStatement st, BlockType type) {
        AbapBlock b = st.getBlock();
        while (b != null) {
            if (b.getType() == type) {
                return b;
            }
            b = b.getParent();
        }
        return null;
    }
}
