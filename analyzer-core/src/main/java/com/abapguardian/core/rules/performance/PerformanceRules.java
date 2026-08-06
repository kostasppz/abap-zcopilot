package com.abapguardian.core.rules.performance;

import com.abapguardian.core.lexer.AbapToken;
import com.abapguardian.core.model.AbapBlock;
import com.abapguardian.core.model.AbapStatement;
import com.abapguardian.core.model.BlockType;
import com.abapguardian.core.rule.AbapRule;
import com.abapguardian.core.rule.AnalysisContext;
import com.abapguardian.core.rule.Finding;
import com.abapguardian.core.rule.RuleCategory;
import com.abapguardian.core.rule.RuleSeverity;
import com.abapguardian.core.rules.AbstractRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.abapguardian.core.rules.RuleSupport.findingAt;
import static com.abapguardian.core.rules.RuleSupport.isDatabaseChange;
import static com.abapguardian.core.rules.RuleSupport.isInLoop;
import static com.abapguardian.core.rules.RuleSupport.isSelect;
import static com.abapguardian.core.rules.RuleSupport.selectTable;

/** Deterministic performance rules. */
public final class PerformanceRules {

    private PerformanceRules() {
    }

    public static List<AbapRule> all() {
        return List.of(
                new SelectInLoop(),
                new DatabaseChangeInLoop(),
                new RfcOrFunctionInLoop(),
                new SelectStar(),
                new SelectWithoutWhere(),
                new ForAllEntriesWithoutEmptyCheck(),
                new NestedStandardTableLoop(),
                new RepeatedSortInLoop(),
                new CommitInLoop(),
                new SelectEndselect(),
                new RepeatedReadTable(),
                new UnboundedInternalTable(),
                new DynamicSql(),
                new UnusedSelectedFields());
    }

    static final class SelectInLoop extends AbstractRule {
        SelectInLoop() {
            super("PERF_SELECT_IN_LOOP", RuleCategory.PERFORMANCE, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (isSelect(st) && isInLoop(st)) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.95)
                            .title("Database SELECT inside a loop")
                            .explanation("Each loop pass performs a separate database round trip. "
                                    + "For n loop entries this causes n database calls instead of one, "
                                    + "which typically dominates runtime for larger data sets.")
                            .recommendation("Read all required rows once before the loop, e.g. with "
                                    + "SELECT ... FOR ALL ENTRIES or a JOIN, into an internal table "
                                    + "(ideally a SORTED or HASHED table), then use READ TABLE inside the loop.")
                            .addDocumentationReference("SAP Help: FOR ALL ENTRIES")
                            .addDocumentationReference("ABAP performance guideline: avoid SELECT in loops")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class DatabaseChangeInLoop extends AbstractRule {
        DatabaseChangeInLoop() {
            super("PERF_DATABASE_CHANGE_IN_LOOP", RuleCategory.PERFORMANCE, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (isDatabaseChange(st) && isInLoop(st)) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.8)
                            .requiresHumanReview(true)
                            .title("Database change inside a loop")
                            .explanation("Single-row INSERT/UPDATE/MODIFY/DELETE statements inside a loop "
                                    + "cause one database round trip per iteration.")
                            .recommendation("Collect changed rows in an internal table and perform one "
                                    + "array operation after the loop (e.g. MODIFY dbtab FROM TABLE lt_data).")
                            .addDocumentationReference("ABAP performance guideline: array database operations")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class RfcOrFunctionInLoop extends AbstractRule {
        RfcOrFunctionInLoop() {
            super("PERF_RFC_OR_FUNCTION_IN_LOOP", RuleCategory.PERFORMANCE, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if ("CALL FUNCTION".equals(st.getLeadingWords(2)) && isInLoop(st)) {
                    boolean rfc = st.containsWord("DESTINATION");
                    findings.add(findingAt(st, getRuleId(), getCategory(),
                            rfc ? RuleSeverity.HIGH : getDefaultSeverity())
                            .confidence(rfc ? 0.9 : 0.6)
                            .requiresHumanReview(!rfc)
                            .title(rfc ? "RFC call inside a loop" : "Function call inside a loop")
                            .explanation(rfc
                                    ? "A remote function call per loop pass multiplies network latency "
                                    + "and remote-system load by the number of iterations."
                                    : "A function module call per loop pass can be expensive depending on "
                                    + "what the function does; verify whether a bulk interface exists.")
                            .recommendation("Check whether the function module offers a table/bulk interface, "
                                    + "or restructure the code to call it once with all data.")
                            .addDocumentationReference("ABAP performance guideline: avoid RFC calls in loops")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class SelectStar extends AbstractRule {
        SelectStar() {
            super("PERF_SELECT_STAR", RuleCategory.PERFORMANCE, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (!isSelect(st)) {
                    continue;
                }
                List<AbapToken> tokens = st.getTokens();
                for (int i = 0; i < tokens.size() - 1 && i < 4; i++) {
                    AbapToken t = tokens.get(i);
                    if ("*".equals(t.getText())) {
                        findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                                .confidence(0.9)
                                .title("SELECT * transfers all columns")
                                .explanation("SELECT * transfers every column of the table even when only a "
                                        + "few fields are needed, increasing database load, network transfer "
                                        + "and memory consumption.")
                                .recommendation("List only the fields you actually use in the field list.")
                                .addDocumentationReference("ABAP SQL guideline: restrict the field list")
                                .build());
                        break;
                    }
                    if (t.isWord() && !"SELECT".equals(t.getUpperText()) && !"SINGLE".equals(t.getUpperText())
                            && !"DISTINCT".equals(t.getUpperText())) {
                        break;
                    }
                }
            }
            return findings;
        }
    }

    static final class SelectWithoutWhere extends AbstractRule {
        SelectWithoutWhere() {
            super("PERF_SELECT_WITHOUT_WHERE", RuleCategory.PERFORMANCE, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (!isSelect(st)) {
                    continue;
                }
                if (st.containsWord("WHERE") || st.containsPhrase("FOR", "ALL", "ENTRIES")) {
                    continue;
                }
                // Aggregations without WHERE may be legitimate; lower confidence.
                boolean aggregate = st.containsWord("COUNT") || st.containsWord("MAX")
                        || st.containsWord("MIN") || st.containsWord("SUM") || st.containsWord("AVG");
                boolean limited = st.containsPhrase("UP", "TO");
                if (limited) {
                    continue;
                }
                findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                        .confidence(aggregate ? 0.5 : 0.85)
                        .requiresHumanReview(aggregate)
                        .title("SELECT without WHERE condition")
                        .explanation("A SELECT without a WHERE clause reads the entire table. On growing "
                                + "productive tables this leads to unbounded runtime and memory usage.")
                        .recommendation("Add a selective WHERE condition, or if a full read is intended, "
                                + "document why and consider packaging with UP TO n ROWS or cursor processing.")
                        .addDocumentationReference("ABAP SQL guideline: always select with WHERE")
                        .build());
            }
            return findings;
        }
    }

    static final class ForAllEntriesWithoutEmptyCheck extends AbstractRule {
        ForAllEntriesWithoutEmptyCheck() {
            super("PERF_FOR_ALL_ENTRIES_WITHOUT_EMPTY_CHECK", RuleCategory.PERFORMANCE, RuleSeverity.CRITICAL);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (!isSelect(st) || !st.containsPhrase("FOR", "ALL", "ENTRIES")) {
                    continue;
                }
                AbapToken itabToken = st.wordAfter("IN");
                String itab = itabToken == null ? null : itabToken.getUpperText();
                if (itab == null) {
                    continue;
                }
                if (!hasEmptyCheck(st, itab)) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.85)
                            .title("FOR ALL ENTRIES without empty-table check")
                            .explanation("If the driver table " + itab + " is empty, FOR ALL ENTRIES selects "
                                    + "ALL rows of the database table because the WHERE clause collapses. "
                                    + "This is a classic cause of production incidents.")
                            .recommendation("Guard the SELECT with IF " + itab.toLowerCase(Locale.ROOT)
                                    + " IS NOT INITIAL. ... ENDIF.")
                            .addDocumentationReference("SAP Help: FOR ALL ENTRIES – empty driver table behavior")
                            .build());
                }
            }
            return findings;
        }

        private boolean hasEmptyCheck(AbapStatement select, String itab) {
            // Enclosing IF/ELSEIF whose condition references the driver table and INITIAL/LINES.
            AbapBlock b = select.getBlock();
            while (b != null) {
                if (b.getType() == BlockType.IF || b.getType() == BlockType.ELSEIF) {
                    AbapStatement cond = b.getOpeningStatement();
                    if (cond != null && cond.containsWord(itab)
                            && (cond.containsWord("INITIAL") || cond.containsWord("LINES"))) {
                        return true;
                    }
                }
                b = b.getParent();
            }
            // CHECK statement shortly before the SELECT in the same block.
            for (AbapStatement prev : select.getBlock().getStatements()) {
                if (prev.getIndex() >= select.getIndex()) {
                    break;
                }
                if ("CHECK".equals(prev.getFirstKeyword()) && prev.containsWord(itab)
                        && (prev.containsWord("INITIAL") || prev.containsWord("LINES"))) {
                    return true;
                }
                if ("RETURN".equals(prev.getFirstKeyword())) {
                    continue;
                }
            }
            return false;
        }
    }

    static final class NestedStandardTableLoop extends AbstractRule {
        NestedStandardTableLoop() {
            super("PERF_NESTED_STANDARD_TABLE_LOOP", RuleCategory.PERFORMANCE, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (!"LOOP".equals(st.getFirstKeyword()) || !st.containsWord("AT")) {
                    continue;
                }
                if (st.containsPhrase("USING", "KEY")) {
                    continue;
                }
                if (isInLoop(st)) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.7)
                            .requiresHumanReview(true)
                            .title("Nested internal table loop")
                            .explanation("A LOOP AT inside another loop scans the inner table once per outer "
                                    + "iteration, giving O(n*m) runtime for standard tables.")
                            .recommendation("Use a SORTED or HASHED table with a key access, a secondary key "
                                    + "(LOOP AT ... USING KEY), or restructure with parallel cursors.")
                            .addDocumentationReference("ABAP performance guideline: nested loops on standard tables")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class RepeatedSortInLoop extends AbstractRule {
        RepeatedSortInLoop() {
            super("PERF_REPEATED_SORT_IN_LOOP", RuleCategory.PERFORMANCE, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if ("SORT".equals(st.getFirstKeyword()) && isInLoop(st)) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.85)
                            .title("SORT inside a loop")
                            .explanation("Sorting inside a loop repeats an O(n log n) operation on every "
                                    + "iteration although the table content usually only needs to be sorted once.")
                            .recommendation("Move the SORT before the loop, or use a SORTED table type so no "
                                    + "explicit sorting is needed.")
                            .addDocumentationReference("ABAP performance guideline: sort once, not per iteration")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class CommitInLoop extends AbstractRule {
        CommitInLoop() {
            super("PERF_COMMIT_IN_LOOP", RuleCategory.PERFORMANCE, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if ("COMMIT".equals(st.getFirstKeyword()) && isInLoop(st)) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.9)
                            .requiresHumanReview(true)
                            .title("COMMIT WORK inside a loop")
                            .explanation("A database commit per iteration forces synchronous update processing, "
                                    + "destroys the logical unit of work and drastically slows down mass processing.")
                            .recommendation("Commit once after the loop, or commit in reasonably sized packages "
                                    + "if restartability requires intermediate commits.")
                            .addDocumentationReference("SAP Help: COMMIT WORK and logical units of work")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class SelectEndselect extends AbstractRule {
        SelectEndselect() {
            super("PERF_SELECT_ENDSELECT", RuleCategory.PERFORMANCE, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            collect(context.getSource().getRootBlock(), findings);
            return findings;
        }

        private void collect(AbapBlock block, List<Finding> findings) {
            if (block.getType() == BlockType.SELECT_LOOP && block.getOpeningStatement() != null) {
                AbapStatement st = block.getOpeningStatement();
                findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                        .confidence(0.9)
                        .title("SELECT ... ENDSELECT row-by-row processing")
                        .explanation("SELECT ... ENDSELECT fetches rows one by one from the database cursor, "
                                + "causing repeated database round trips.")
                        .recommendation("Read the result set at once with SELECT ... INTO TABLE and process "
                                + "the internal table with LOOP AT.")
                        .addDocumentationReference("ABAP SQL guideline: prefer INTO TABLE over SELECT/ENDSELECT")
                        .build());
            }
            for (AbapBlock child : block.getChildren()) {
                collect(child, findings);
            }
        }
    }

    static final class RepeatedReadTable extends AbstractRule {
        RepeatedReadTable() {
            super("PERF_REPEATED_READ_TABLE", RuleCategory.PERFORMANCE, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (!"READ".equals(st.getFirstKeyword()) || !st.containsWord("TABLE")) {
                    continue;
                }
                if (!isInLoop(st)) {
                    continue;
                }
                if (st.containsPhrase("BINARY", "SEARCH") || st.containsPhrase("USING", "KEY")
                        || st.containsPhrase("WITH", "TABLE", "KEY")) {
                    continue;
                }
                if (!st.containsWord("KEY")) {
                    continue; // index access is O(1)
                }
                findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                        .confidence(0.7)
                        .requiresHumanReview(true)
                        .title("Linear READ TABLE ... WITH KEY inside a loop")
                        .explanation("READ TABLE with a free key on a standard table performs a linear scan. "
                                + "Inside a loop this leads to O(n*m) runtime.")
                        .recommendation("Use a SORTED/HASHED table, a secondary key, or BINARY SEARCH on a "
                                + "table that is sorted by the access key.")
                        .addDocumentationReference("ABAP performance guideline: table access in loops")
                        .build());
            }
            return findings;
        }
    }

    static final class UnboundedInternalTable extends AbstractRule {
        UnboundedInternalTable() {
            super("PERF_UNBOUNDED_INTERNAL_TABLE", RuleCategory.PERFORMANCE, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (!isSelect(st)) {
                    continue;
                }
                boolean intoTable = st.containsPhrase("INTO", "TABLE") || st.containsPhrase("APPENDING", "TABLE");
                if (!intoTable) {
                    continue;
                }
                boolean bounded = st.containsWord("WHERE") || st.containsPhrase("UP", "TO")
                        || st.containsPhrase("FOR", "ALL", "ENTRIES");
                boolean appendingInLoop = st.containsPhrase("APPENDING", "TABLE") && isInLoop(st);
                if (!bounded || appendingInLoop) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(appendingInLoop ? 0.75 : 0.8)
                            .requiresHumanReview(true)
                            .title("Internal table filled without a bound")
                            .explanation(appendingInLoop
                                    ? "APPENDING TABLE inside a loop grows the internal table on every "
                                    + "iteration; memory usage is unbounded for large data sets."
                                    : "The internal table is filled from an unrestricted SELECT; its size "
                                    + "grows with the database table and can exhaust memory.")
                            .recommendation("Restrict the result set with WHERE and/or UP TO n ROWS, or "
                                    + "process the data in packages (PACKAGE SIZE).")
                            .addDocumentationReference("ABAP performance guideline: bounded result sets")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class DynamicSql extends AbstractRule {
        DynamicSql() {
            super("PERF_DYNAMIC_SQL", RuleCategory.PERFORMANCE, RuleSeverity.LOW);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (!isSelect(st)) {
                    continue;
                }
                if (hasDynamicPart(st)) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.8)
                            .requiresHumanReview(true)
                            .title("Dynamic SQL clause")
                            .explanation("Dynamic table names or WHERE clauses prevent static optimization "
                                    + "and statement caching, and shift errors from compile time to runtime.")
                            .recommendation("Prefer static SQL. If dynamism is unavoidable, isolate and "
                                    + "validate the dynamic parts (see also SEC_DYNAMIC_SQL_INPUT).")
                            .addDocumentationReference("ABAP SQL guideline: static versus dynamic SQL")
                            .build());
                }
            }
            return findings;
        }

        static boolean hasDynamicPart(AbapStatement st) {
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
    }

    static final class UnusedSelectedFields extends AbstractRule {
        UnusedSelectedFields() {
            super("PERF_UNUSED_SELECTED_FIELDS", RuleCategory.PERFORMANCE, RuleSeverity.LOW);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (!isSelect(st)) {
                    continue;
                }
                List<String> fieldList = explicitFieldList(st);
                String target = intoStructureName(st);
                if (fieldList.size() < 2 || target == null) {
                    continue;
                }
                Set<String> used = usedComponents(context, st, target);
                List<String> unused = new ArrayList<>();
                for (String field : fieldList) {
                    if (!used.contains(field)) {
                        unused.add(field);
                    }
                }
                if (!unused.isEmpty() && unused.size() < fieldList.size()) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.5)
                            .requiresHumanReview(true)
                            .title("Selected fields appear unused: " + String.join(", ", unused))
                            .explanation("Fields that are selected but never read afterwards increase "
                                    + "database transfer volume without benefit. This check is heuristic; "
                                    + "usage through field symbols or dynamic access is not tracked.")
                            .recommendation("Remove unused fields from the SELECT field list.")
                            .addDocumentationReference("ABAP SQL guideline: restrict the field list")
                            .build());
                }
            }
            return findings;
        }

        private List<String> explicitFieldList(AbapStatement st) {
            List<String> fields = new ArrayList<>();
            List<AbapToken> tokens = st.getTokens();
            boolean afterSelect = false;
            for (AbapToken t : tokens) {
                if (t.isWord()) {
                    String w = t.getUpperText();
                    if ("SELECT".equals(w)) {
                        afterSelect = true;
                        continue;
                    }
                    if (!afterSelect) {
                        continue;
                    }
                    if ("SINGLE".equals(w) || "DISTINCT".equals(w)) {
                        continue;
                    }
                    if ("FROM".equals(w) || "INTO".equals(w) || "UP".equals(w)) {
                        break;
                    }
                    fields.add(w);
                } else if ("*".equals(t.getText())) {
                    return List.of();
                }
            }
            return fields;
        }

        private String intoStructureName(AbapStatement st) {
            List<AbapToken> tokens = st.getTokens();
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).isWord() && tokens.get(i).matches("INTO")) {
                    for (int j = i + 1; j < tokens.size(); j++) {
                        AbapToken t = tokens.get(j);
                        if (t.isWord()) {
                            String w = t.getUpperText();
                            if ("TABLE".equals(w) || "CORRESPONDING".equals(w) || "FIELDS".equals(w)
                                    || "OF".equals(w) || "DATA".equals(w)) {
                                if ("DATA".equals(w)) {
                                    // INTO @DATA(ls_x)
                                    for (int k = j + 1; k < tokens.size(); k++) {
                                        if (tokens.get(k).isWord()) {
                                            return tokens.get(k).getUpperText();
                                        }
                                    }
                                }
                                continue;
                            }
                            return w;
                        }
                    }
                }
            }
            return null;
        }

        private Set<String> usedComponents(AnalysisContext context, AbapStatement select, String target) {
            Set<String> used = new HashSet<>();
            String prefix = target.toUpperCase(Locale.ROOT) + "-";
            for (AbapStatement st : context.getSource().statementsAfter(select)) {
                for (AbapToken t : st.wordTokens()) {
                    String w = t.getUpperText();
                    if (w.startsWith(prefix)) {
                        used.add(w.substring(prefix.length()));
                    } else if (w.equals(target.toUpperCase(Locale.ROOT))) {
                        // whole-structure usage: treat all fields as used
                        return new HashSet<>(List.of("*ALL*")) {
                            @Override
                            public boolean contains(Object o) {
                                return true;
                            }
                        };
                    }
                }
                for (AbapToken t : st.literalTokens()) {
                    String upper = t.getUpperText();
                    if (upper.contains(prefix)) {
                        int idx = upper.indexOf(prefix);
                        while (idx >= 0) {
                            int end = idx + prefix.length();
                            StringBuilder sb = new StringBuilder();
                            while (end < upper.length()
                                    && (Character.isLetterOrDigit(upper.charAt(end)) || upper.charAt(end) == '_')) {
                                sb.append(upper.charAt(end));
                                end++;
                            }
                            if (sb.length() > 0) {
                                used.add(sb.toString());
                            }
                            idx = upper.indexOf(prefix, end);
                        }
                    }
                }
            }
            return used;
        }
    }
}
