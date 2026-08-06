package com.abapguardian.core.rules.privacy;

import com.abapguardian.core.config.RuleConfiguration;
import com.abapguardian.core.lexer.AbapToken;
import com.abapguardian.core.model.AbapStatement;
import com.abapguardian.core.rule.AbapRule;
import com.abapguardian.core.rule.AnalysisContext;
import com.abapguardian.core.rule.Finding;
import com.abapguardian.core.rule.RuleCategory;
import com.abapguardian.core.rule.RuleSeverity;
import com.abapguardian.core.rules.AbstractRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.abapguardian.core.rules.RuleSupport.callsFunction;
import static com.abapguardian.core.rules.RuleSupport.codeContains;
import static com.abapguardian.core.rules.RuleSupport.findingAt;
import static com.abapguardian.core.rules.RuleSupport.isSelect;
import static com.abapguardian.core.rules.RuleSupport.referencesSensitiveData;
import static com.abapguardian.core.rules.RuleSupport.selectTable;

/**
 * Deterministic privacy rules.
 *
 * <p>Sensitive identifiers are configurable. Presence of a sensitive
 * identifier alone never triggers a finding; the statement context (data
 * destination such as log, message, spool, file, external transfer) is what
 * makes it reportable, and confidence values reflect the remaining
 * uncertainty. All privacy findings require human review.
 */
public final class PrivacyRules {

    private PrivacyRules() {
    }

    public static List<AbapRule> all() {
        return List.of(
                new PersonalDataInLog(),
                new PersonalDataInMessage(),
                new PersonalDataInSpool(),
                new PersonalDataInFileExport(),
                new BroadHrMasterDataSelection(),
                new UnmaskedPersonnelNumber(),
                new ExternalDataTransfer(),
                new ExcessiveFieldSelection(),
                new DebugOutputOfPersonalData());
    }

    private static Finding.Builder privacyFinding(AbapStatement st, AbstractRule rule, double confidence) {
        return findingAt(st, rule.getRuleId(), rule.getCategory(), rule.getDefaultSeverity())
                .confidence(confidence)
                .requiresHumanReview(true);
    }

    static final class PersonalDataInLog extends AbstractRule {
        PersonalDataInLog() {
            super("PRIV_PERSONAL_DATA_IN_LOG", RuleCategory.PRIVACY, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            RuleConfiguration config = context.getConfiguration();
            for (AbapStatement st : context.getSource().getStatements()) {
                boolean logContext =
                        callsFunction(st, "BAL_LOG")
                        || "LOG-POINT".equals(st.getFirstKeyword())
                        || st.containsWord("LOG-POINT");
                if (logContext && referencesSensitiveData(st, config)) {
                    findings.add(privacyFinding(st, this, 0.7)
                            .title("Personal data possibly written to an application log")
                            .explanation("Sensitive identifiers appear in a logging statement. Application "
                                    + "logs are broadly readable and often retained for a long time; personal "
                                    + "data in logs may violate data-minimization and retention policies.")
                            .recommendation("Log technical keys only, or mask/pseudonymize personal fields "
                                    + "before logging. If logging is required and approved, suppress this "
                                    + "finding with a documented reason.")
                            .addDocumentationReference("GDPR Art. 5(1)(c) data minimisation")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class PersonalDataInMessage extends AbstractRule {
        PersonalDataInMessage() {
            super("PRIV_PERSONAL_DATA_IN_MESSAGE", RuleCategory.PRIVACY, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            RuleConfiguration config = context.getConfiguration();
            for (AbapStatement st : context.getSource().getStatements()) {
                if ("MESSAGE".equals(st.getFirstKeyword()) && referencesSensitiveData(st, config)) {
                    findings.add(privacyFinding(st, this, 0.65)
                            .title("Personal data possibly shown in a message")
                            .explanation("Sensitive identifiers are used in a MESSAGE statement. Messages "
                                    + "can appear on shared screens, in batch job logs and in support "
                                    + "screenshots.")
                            .recommendation("Show technical keys or masked values in messages; keep personal "
                                    + "details out of user-facing text.")
                            .addDocumentationReference("GDPR Art. 5(1)(c) data minimisation")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class PersonalDataInSpool extends AbstractRule {
        PersonalDataInSpool() {
            super("PRIV_PERSONAL_DATA_IN_SPOOL", RuleCategory.PRIVACY, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            RuleConfiguration config = context.getConfiguration();
            for (AbapStatement st : context.getSource().getStatements()) {
                boolean spoolContext = "WRITE".equals(st.getFirstKeyword())
                        || "PRINT-CONTROL".equals(st.getFirstKeyword())
                        || ("NEW-PAGE".equals(st.getFirstKeyword()) && st.containsWord("PRINT"));
                if (spoolContext && referencesSensitiveData(st, config)) {
                    findings.add(privacyFinding(st, this, 0.7)
                            .title("Personal data possibly written to list/spool output")
                            .explanation("Sensitive identifiers are written to classic list output. List "
                                    + "output ends up in spool requests, which are retained, reprintable and "
                                    + "often readable by administrators.")
                            .recommendation("Restrict output to required technical fields, mask personal "
                                    + "values, and check who can read the spool (S_SPO_ACT).")
                            .addDocumentationReference("GDPR Art. 5(1)(c) data minimisation")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class PersonalDataInFileExport extends AbstractRule {
        PersonalDataInFileExport() {
            super("PRIV_PERSONAL_DATA_IN_FILE_EXPORT", RuleCategory.PRIVACY, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            RuleConfiguration config = context.getConfiguration();
            for (AbapStatement st : context.getSource().getStatements()) {
                boolean fileContext = "TRANSFER".equals(st.getFirstKeyword())
                        || callsFunction(st, "GUI_DOWNLOAD")
                        || st.containsWord("CL_GUI_FRONTEND_SERVICES");
                if (!fileContext) {
                    continue;
                }
                boolean sensitive = referencesSensitiveData(st, config)
                        || contextReferencesSensitiveTable(context, st);
                if (sensitive) {
                    findings.add(privacyFinding(st, this, 0.6)
                            .title("Personal data possibly exported to a file")
                            .explanation("Data linked to sensitive identifiers is written to a file. Files "
                                    + "leave the controlled database environment and are hard to protect, "
                                    + "audit or delete.")
                            .recommendation("Export only required, minimized fields; encrypt files with "
                                    + "personal data; verify the export has a documented legal basis.")
                            .addDocumentationReference("GDPR Art. 32 security of processing")
                            .build());
                }
            }
            return findings;
        }

        private boolean contextReferencesSensitiveTable(AnalysisContext context, AbapStatement st) {
            // The exported table variable was filled from a sensitive table earlier.
            for (AbapToken t : st.wordTokens()) {
                String var = t.getUpperText();
                if (!var.startsWith("LT_") && !var.startsWith("GT_") && !var.startsWith("IT_")) {
                    continue;
                }
                for (AbapStatement prev : context.getSource().statementsBefore(st)) {
                    if (isSelect(prev) && prev.containsWord(var)
                            && context.getConfiguration().isSensitiveTable(selectTable(prev))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    static final class BroadHrMasterDataSelection extends AbstractRule {
        BroadHrMasterDataSelection() {
            super("PRIV_BROAD_HR_MASTER_DATA_SELECTION", RuleCategory.PRIVACY, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (!isSelect(st)) {
                    continue;
                }
                String table = selectTable(st);
                if (!context.getConfiguration().isSensitiveTable(table)) {
                    continue;
                }
                boolean selectStar = st.getTokens().stream().limit(5)
                        .anyMatch(t -> "*".equals(t.getText()));
                boolean noWhere = !st.containsWord("WHERE") && !st.containsPhrase("FOR", "ALL", "ENTRIES");
                if (selectStar || noWhere) {
                    findings.add(privacyFinding(st, this, selectStar && noWhere ? 0.85 : 0.7)
                            .title("Broad selection from HR/personal master data table " + table)
                            .explanation("The SELECT reads " + (selectStar ? "all columns" : "rows")
                                    + (noWhere ? " without restriction" : "") + " from " + table
                                    + ", which contains personal data. Reading more personal data than "
                                    + "needed violates the data-minimization principle.")
                            .recommendation("Select only the specific fields and rows required for the "
                                    + "business purpose, and verify authorization checks (P_ORGIN/P_PERNR).")
                            .addDocumentationReference("GDPR Art. 5(1)(c) data minimisation")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class UnmaskedPersonnelNumber extends AbstractRule {
        UnmaskedPersonnelNumber() {
            super("PRIV_UNMASKED_PERSONNEL_NUMBER", RuleCategory.PRIVACY, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                String kw = st.getFirstKeyword();
                boolean outputContext = "WRITE".equals(kw) || "MESSAGE".equals(kw);
                if (!outputContext) {
                    continue;
                }
                boolean pernr = st.wordTokens().stream().anyMatch(t -> {
                    String w = t.getUpperText();
                    return w.equals("PERNR") || w.endsWith("-PERNR") || w.endsWith("_PERNR");
                });
                boolean masked = st.containsWord("MASK") || codeContains(st, "CL_ABAP_MASK");
                if (pernr && !masked) {
                    findings.add(privacyFinding(st, this, 0.6)
                            .title("Personnel number output without masking")
                            .explanation("A personnel number is written to output without masking. PERNR "
                                    + "directly identifies an employee and should not be broadly displayed.")
                            .recommendation("Mask or pseudonymize the personnel number in output, or verify "
                                    + "the audience is authorized to see it.")
                            .addDocumentationReference("GDPR Art. 25 data protection by design")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class ExternalDataTransfer extends AbstractRule {
        ExternalDataTransfer() {
            super("PRIV_EXTERNAL_DATA_TRANSFER", RuleCategory.PRIVACY, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            RuleConfiguration config = context.getConfiguration();
            boolean sensitiveInScope = context.getSource().getStatements().stream()
                    .anyMatch(st -> referencesSensitiveData(st, config)
                            || (isSelect(st) && config.isSensitiveTable(selectTable(st))));
            if (!sensitiveInScope) {
                return findings;
            }
            for (AbapStatement st : context.getSource().getStatements()) {
                String destination = destinationOf(st);
                if (destination == null) {
                    continue;
                }
                if (config.getApprovedDestinations().contains(destination.toUpperCase(Locale.ROOT))) {
                    continue;
                }
                boolean http = st.containsWord("CL_HTTP_CLIENT");
                findings.add(privacyFinding(st, this, destination.isEmpty() ? 0.5 : 0.65)
                        .title("Data possibly transferred to a non-approved external destination")
                        .explanation("This source unit handles personal data and also communicates with an "
                                + (http ? "HTTP endpoint" : "RFC destination")
                                + (destination.isEmpty() ? "" : " ('" + destination + "')")
                                + " that is not in the configured approved-destinations list.")
                        .recommendation("Confirm the destination is approved for personal data, add it to "
                                + "the approvedDestinations configuration if so, and ensure a processing "
                                + "agreement covers the transfer.")
                        .addDocumentationReference("GDPR Chapter V data transfers")
                        .build());
            }
            return findings;
        }

        private String destinationOf(AbapStatement st) {
            if ("CALL FUNCTION".equals(st.getLeadingWords(2)) && st.containsWord("DESTINATION")) {
                AbapToken t = st.tokenAfterWord("DESTINATION");
                if (t == null) {
                    return "";
                }
                String text = t.getText();
                if (text.length() >= 2 && (text.startsWith("'") || text.startsWith("`"))) {
                    return text.substring(1, text.length() - 1);
                }
                return "";
            }
            if (st.containsWord("CL_HTTP_CLIENT")
                    && codeContains(st, "CREATE_BY")) {
                return "";
            }
            return null;
        }
    }

    static final class ExcessiveFieldSelection extends AbstractRule {
        ExcessiveFieldSelection() {
            super("PRIV_EXCESSIVE_FIELD_SELECTION", RuleCategory.PRIVACY, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            RuleConfiguration config = context.getConfiguration();
            int threshold = config.getExcessiveFieldThreshold();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (!isSelect(st)) {
                    continue;
                }
                int sensitiveFieldCount = 0;
                List<String> fields = new ArrayList<>();
                boolean beforeFrom = true;
                for (AbapToken t : st.getTokens()) {
                    if (t.isWord() && t.matches("FROM")) {
                        beforeFrom = false;
                    }
                    if (beforeFrom && t.isWord() && config.getSensitiveFields().contains(t.getUpperText())) {
                        sensitiveFieldCount++;
                        fields.add(t.getUpperText());
                    }
                }
                if (sensitiveFieldCount > threshold) {
                    findings.add(privacyFinding(st, this, 0.6)
                            .title("SELECT reads many sensitive fields (" + sensitiveFieldCount + ")")
                            .explanation("The field list contains " + sensitiveFieldCount
                                    + " configured sensitive fields (" + String.join(", ", fields)
                                    + "), more than the configured threshold of " + threshold
                                    + ". Broad personal-data reads should be limited to what the business "
                                    + "purpose requires.")
                            .recommendation("Reduce the field list to the minimum necessary set, or raise "
                                    + "the threshold if this breadth is justified and documented.")
                            .addDocumentationReference("GDPR Art. 5(1)(c) data minimisation")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class DebugOutputOfPersonalData extends AbstractRule {
        DebugOutputOfPersonalData() {
            super("PRIV_DEBUG_OUTPUT_OF_PERSONAL_DATA", RuleCategory.PRIVACY, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            RuleConfiguration config = context.getConfiguration();
            for (AbapStatement st : context.getSource().getStatements()) {
                boolean debugContext = st.containsWord("CL_DEMO_OUTPUT")
                        || "BREAK-POINT".equals(st.getFirstKeyword())
                        || st.containsWord("ASSERT") && st.containsWord("FIELDS");
                if (debugContext && (referencesSensitiveData(st, config)
                        || "BREAK-POINT".equals(st.getFirstKeyword()) && nearSensitive(context, st))) {
                    findings.add(privacyFinding(st, this, 0.55)
                            .title("Debug output near personal data")
                            .explanation("Debug or demo output constructs appear together with sensitive "
                                    + "identifiers. Debug output must never reach productive systems with "
                                    + "personal data.")
                            .recommendation("Remove debug output before transport; use masked test data.")
                            .addDocumentationReference("GDPR Art. 32 security of processing")
                            .build());
                }
            }
            return findings;
        }

        private boolean nearSensitive(AnalysisContext context, AbapStatement st) {
            int idx = st.getIndex();
            List<AbapStatement> all = context.getSource().getStatements();
            for (int i = Math.max(0, idx - 3); i < Math.min(all.size(), idx + 4); i++) {
                if (referencesSensitiveData(all.get(i), context.getConfiguration())) {
                    return true;
                }
            }
            return false;
        }
    }
}
