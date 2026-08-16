package com.abapguardian.core.rules.s4hana;

import com.abapguardian.core.lexer.AbapToken;
import com.abapguardian.core.model.AbapStatement;
import com.abapguardian.core.rule.AbapRule;
import com.abapguardian.core.rule.AnalysisContext;
import com.abapguardian.core.rule.Finding;
import com.abapguardian.core.rule.RuleCategory;
import com.abapguardian.core.rule.RuleSeverity;
import com.abapguardian.core.rules.AbstractRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.abapguardian.core.rules.RuleSupport.codeContains;
import static com.abapguardian.core.rules.RuleSupport.findingAt;

/** Deterministic checks for common SAP S/4HANA migration risks. */
public final class S4HanaRules {

    private S4HanaRules() {
    }

    public static List<AbapRule> all() {
        return List.of(
                new SimplifiedDataModelTable(),
                new HeaderLine(),
                new Occurs(),
                new NativeSql(),
                new DatabaseHint());
    }

    private record Replacement(String target, String example) {
    }

    private static final Map<String, Replacement> SIMPLIFIED_TABLES = simplifiedTables();

    private static Map<String, Replacement> simplifiedTables() {
        Map<String, Replacement> tables = new LinkedHashMap<>();
        Replacement journal = new Replacement(
                "a released journal-entry CDS view (ACDOCA data model)",
                "SELECT ... FROM I_JournalEntryItem ...");
        for (String table : List.of("BSIK", "BSAK", "BSID", "BSAD", "BSIS", "BSAS",
                "COEP", "COSP", "COSS")) {
            tables.put(table, journal);
        }
        tables.put("MSEG", new Replacement(
                "I_MaterialDocumentItem or another released material-document API",
                "SELECT ... FROM I_MaterialDocumentItem ..."));
        tables.put("MKPF", new Replacement(
                "I_MaterialDocumentHeader or another released material-document API",
                "SELECT ... FROM I_MaterialDocumentHeader ..."));
        tables.put("KONV", new Replacement(
                "a released pricing-condition CDS view/API for the PRCD_ELEMENTS data model",
                "SELECT ... FROM <released pricing CDS view> ..."));
        tables.put("VBUK", new Replacement(
                "released sales-document CDS status fields/APIs",
                "SELECT ... FROM I_SalesDocument ..."));
        tables.put("VBUP", new Replacement(
                "released sales-document-item CDS status fields/APIs",
                "SELECT ... FROM I_SalesDocumentItem ..."));
        return Map.copyOf(tables);
    }

    static final class SimplifiedDataModelTable extends AbstractRule {
        SimplifiedDataModelTable() {
            super("S4_SIMPLIFIED_DATA_MODEL_TABLE", RuleCategory.S4HANA, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement statement : context.getSource().getStatements()) {
                String table = referencedSqlTable(statement);
                if (table == null) {
                    continue;
                }
                Replacement replacement = SIMPLIFIED_TABLES.get(table);
                if (replacement == null) {
                    continue;
                }
                findings.add(findingAt(statement, getRuleId(), getCategory(), getDefaultSeverity())
                        .confidence(0.9)
                        .requiresHumanReview(true)
                        .title("Review direct access to " + table + " for S/4HANA")
                        .explanation(table + " belongs to an application area with a simplified S/4HANA "
                                + "data model. Compatibility views can hide functional and performance "
                                + "differences, and direct changes are unsafe.")
                        .recommendation("Map the required fields and business semantics to "
                                + replacement.target() + ". Validate the replacement with the relevant "
                                + "SAP S/4HANA simplification item and ATC migration checks.")
                        .suggestedCode(replacement.example())
                        .addDocumentationReference("SAP S/4HANA Simplification Item Catalog")
                        .addDocumentationReference("SAP ATC S/4HANA readiness checks")
                        .build());
            }
            return findings;
        }

        private String referencedSqlTable(AbapStatement statement) {
            List<AbapToken> words = statement.wordTokens();
            for (int i = 0; i < words.size(); i++) {
                String current = words.get(i).getUpperText();
                if (!SIMPLIFIED_TABLES.containsKey(current) || i == 0) {
                    continue;
                }
                String previous = words.get(i - 1).getUpperText();
                if ("FROM".equals(previous) || "JOIN".equals(previous)
                        || "UPDATE".equals(previous) || "MODIFY".equals(previous)
                        || "INSERT".equals(previous) || "DELETE".equals(previous)) {
                    return current;
                }
            }
            return null;
        }
    }

    static final class HeaderLine extends AbstractRule {
        HeaderLine() {
            super("S4_WITH_HEADER_LINE", RuleCategory.S4HANA, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement statement : context.getSource().getStatements()) {
                if (statement.containsPhrase("WITH", "HEADER", "LINE")) {
                    findings.add(findingAt(statement, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.98)
                            .title("Obsolete internal table header line")
                            .explanation("Internal tables with implicit header lines are obsolete and make "
                                    + "table and work-area access ambiguous in modern S/4HANA code.")
                            .recommendation("Declare the internal table and work area separately, then use "
                                    + "explicit INTO, ASSIGNING or REFERENCE INTO additions.")
                            .suggestedCode("DATA lt_items TYPE STANDARD TABLE OF <line_type> WITH EMPTY KEY.\n"
                                    + "DATA ls_item TYPE <line_type>.")
                            .addDocumentationReference("ABAP Keyword Documentation: Obsolete Declarations")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class Occurs extends AbstractRule {
        Occurs() {
            super("S4_OCCURS_DECLARATION", RuleCategory.S4HANA, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement statement : context.getSource().getStatements()) {
                if ("DATA".equals(statement.getFirstKeyword()) && statement.containsWord("OCCURS")) {
                    findings.add(findingAt(statement, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.98)
                            .title("Obsolete OCCURS declaration")
                            .explanation("OCCURS is obsolete ABAP syntax and couples an internal-table "
                                    + "declaration to an outdated initial-memory hint.")
                            .recommendation("Declare a typed STANDARD, SORTED or HASHED table with an "
                                    + "explicit key that matches its access pattern.")
                            .suggestedCode("DATA lt_items TYPE STANDARD TABLE OF <line_type> WITH EMPTY KEY.")
                            .addDocumentationReference("ABAP Keyword Documentation: DATA, obsolete variants")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class NativeSql extends AbstractRule {
        NativeSql() {
            super("S4_NATIVE_SQL", RuleCategory.S4HANA, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement statement : context.getSource().getStatements()) {
                if ("EXEC".equals(statement.getFirstKeyword()) && statement.containsWord("SQL")) {
                    findings.add(findingAt(statement, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.95)
                            .requiresHumanReview(true)
                            .title("Native SQL requires S/4HANA review")
                            .explanation("Native SQL bypasses the ABAP SQL portability and semantic layers. "
                                    + "It can depend on database-specific objects changed by an S/4HANA migration.")
                            .recommendation("Replace it with ABAP SQL over a released CDS view or a released "
                                    + "application API. If native SQL is unavoidable, document and test the "
                                    + "database dependency explicitly.")
                            .suggestedCode("SELECT ... FROM <released CDS view> ...")
                            .addDocumentationReference("SAP Clean Core: use released APIs")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class DatabaseHint extends AbstractRule {
        DatabaseHint() {
            super("S4_DATABASE_HINT", RuleCategory.S4HANA, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement statement : context.getSource().getStatements()) {
                if (codeContains(statement, "HINTS")) {
                    findings.add(findingAt(statement, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.85)
                            .requiresHumanReview(true)
                            .title("Database-specific SQL hint")
                            .explanation("Database hints couple the statement to a particular optimizer and "
                                    + "can become invalid or harmful after moving to SAP HANA.")
                            .recommendation("Remove the hint and verify the access path on SAP HANA. Model "
                                    + "the access with appropriate CDS views and keys before adding any "
                                    + "HANA-specific hint based on measured evidence.")
                            .suggestedCode("SELECT ... FROM <released CDS view> ...")
                            .addDocumentationReference("ABAP SQL performance guidelines for SAP HANA")
                            .build());
                }
            }
            return findings;
        }
    }
}
