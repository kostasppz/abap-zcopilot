package com.abapguardian.core.rules.security;

import com.abapguardian.core.lexer.AbapToken;
import com.abapguardian.core.lexer.TokenType;
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
import java.util.regex.Pattern;

import static com.abapguardian.core.rules.RuleSupport.callsFunction;
import static com.abapguardian.core.rules.RuleSupport.codeContains;
import static com.abapguardian.core.rules.RuleSupport.findingAt;
import static com.abapguardian.core.rules.RuleSupport.hasDynamicSqlPart;
import static com.abapguardian.core.rules.RuleSupport.isDatabaseChange;
import static com.abapguardian.core.rules.RuleSupport.isSelect;

/** Deterministic security rules. */
public final class SecurityRules {

    private SecurityRules() {
    }

    public static List<AbapRule> all() {
        return List.of(
                new HardcodedPassword(),
                new HardcodedToken(),
                new DynamicSqlInput(),
                new DynamicAbapGeneration(),
                new UnsafeCallTransaction(),
                new OsCommand(),
                new UnsafeDatasetPath(),
                new UnvalidatedHttpTarget(),
                new AuthorizationCheckIndicator(),
                new ClientSpecified(),
                new MissingSySubrcHandling());
    }

    private static final Pattern PASSWORD_NAME =
            Pattern.compile(".*(PASSWORD|PASSWD|_PWD|^PWD|PASSWORT).*");
    private static final Pattern TOKEN_NAME =
            Pattern.compile(".*(TOKEN|API_?KEY|SECRET|BEARER|CLIENT_?SECRET|PRIVATE_?KEY).*");
    private static final Pattern TOKEN_LITERAL = Pattern.compile(
            "(?i)('|`)?(bearer\\s+\\S{8,}|gh[pousr]_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9]{20,}|[A-Za-z0-9+/=_-]{32,})('|`)?");

    /** Non-empty literal appearing after an assignment-ish context near a named identifier. */
    private static AbapToken literalNear(AbapStatement st, Pattern namePattern) {
        List<AbapToken> tokens = st.getTokens();
        boolean nameSeen = false;
        for (AbapToken t : tokens) {
            if (t.isWord() && namePattern.matcher(t.getUpperText()).matches()) {
                nameSeen = true;
                continue;
            }
            if (nameSeen && (t.getType() == TokenType.STRING_LITERAL
                    || t.getType() == TokenType.BACKQUOTE_LITERAL)) {
                String inner = t.getText().length() >= 2
                        ? t.getText().substring(1, t.getText().length() - 1) : "";
                if (!inner.trim().isEmpty() && !"*".equals(inner.trim())) {
                    return t;
                }
            }
        }
        return null;
    }

    static final class HardcodedPassword extends AbstractRule {
        HardcodedPassword() {
            super("SEC_HARDCODED_PASSWORD", RuleCategory.SECURITY, RuleSeverity.CRITICAL);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                AbapToken literal = literalNear(st, PASSWORD_NAME);
                if (literal != null) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.85)
                            .requiresHumanReview(true)
                            .title("Possible hard-coded password")
                            .explanation("A literal value is assigned to an identifier that looks like a "
                                    + "password. Hard-coded credentials are readable by anyone with source "
                                    + "access and survive in transports and version history.")
                            .recommendation("Remove the literal. Use SAP secure storage (SSF/secstore), "
                                    + "RFC destinations with stored credentials, or prompt the user.")
                            .addDocumentationReference("CWE-798: Use of Hard-coded Credentials")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class HardcodedToken extends AbstractRule {
        HardcodedToken() {
            super("SEC_HARDCODED_TOKEN", RuleCategory.SECURITY, RuleSeverity.CRITICAL);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                AbapToken literal = literalNear(st, TOKEN_NAME);
                boolean matched = literal != null;
                if (!matched) {
                    for (AbapToken t : st.literalTokens()) {
                        String inner = t.getText();
                        if (inner.length() > 34 && TOKEN_LITERAL.matcher(inner).matches()) {
                            matched = true;
                            break;
                        }
                        if (inner.toUpperCase(Locale.ROOT).contains("BEARER ")) {
                            matched = true;
                            break;
                        }
                    }
                }
                if (matched) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.75)
                            .requiresHumanReview(true)
                            .title("Possible hard-coded token or API key")
                            .explanation("A literal that looks like an access token, API key or secret is "
                                    + "embedded in the source code.")
                            .recommendation("Store secrets in SAP secure storage or an external credential "
                                    + "store and rotate any value that was committed.")
                            .addDocumentationReference("CWE-798: Use of Hard-coded Credentials")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class DynamicSqlInput extends AbstractRule {
        DynamicSqlInput() {
            super("SEC_DYNAMIC_SQL_INPUT", RuleCategory.SECURITY, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                boolean dynamicSelect = isSelect(st)
                        && hasDynamicSqlPart(st);
                boolean adbc = st.containsWord("CL_SQL_STATEMENT") || st.containsWord("CL_SQL_PREPARED_STATEMENT");
                boolean execSql = "EXEC".equals(st.getFirstKeyword()) && st.containsWord("SQL");
                boolean dynamicWhereDelete = ("DELETE".equals(st.getFirstKeyword())
                        || "UPDATE".equals(st.getFirstKeyword()))
                        && hasDynamicSqlPart(st);
                if (dynamicSelect || adbc || execSql || dynamicWhereDelete) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(adbc || execSql ? 0.7 : 0.75)
                            .requiresHumanReview(true)
                            .title("Dynamic SQL – potential injection point")
                            .explanation("Dynamically assembled SQL clauses can be manipulated when any part "
                                    + "of the clause originates from user input (SQL injection).")
                            .recommendation("Use static SQL where possible. Validate dynamic identifiers "
                                    + "against an allow-list and escape values with "
                                    + "cl_abap_dyn_prg=>quote / check_table_name_str.")
                            .addDocumentationReference("CWE-89: SQL Injection")
                            .addDocumentationReference("SAP Help: CL_ABAP_DYN_PRG")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class DynamicAbapGeneration extends AbstractRule {
        DynamicAbapGeneration() {
            super("SEC_DYNAMIC_ABAP_GENERATION", RuleCategory.SECURITY, RuleSeverity.CRITICAL);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                boolean generate = "GENERATE".equals(st.getFirstKeyword())
                        && (st.containsWord("SUBROUTINE") || st.containsWord("REPORT"));
                boolean insertReport = st.containsPhrase("INSERT", "REPORT");
                if (generate || insertReport) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.9)
                            .requiresHumanReview(true)
                            .title("Dynamic ABAP code generation")
                            .explanation("GENERATE SUBROUTINE POOL / INSERT REPORT creates and executes code "
                                    + "at runtime. If any input flows into the generated source, this is "
                                    + "arbitrary code execution.")
                            .recommendation("Avoid runtime code generation. If unavoidable, never include "
                                    + "external input and restrict callers via authorization checks.")
                            .addDocumentationReference("CWE-94: Code Injection")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class UnsafeCallTransaction extends AbstractRule {
        UnsafeCallTransaction() {
            super("SEC_UNSAFE_CALL_TRANSACTION", RuleCategory.SECURITY, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (!"CALL TRANSACTION".equals(st.getLeadingWords(2))) {
                    continue;
                }
                if (st.containsPhrase("WITH", "AUTHORITY-CHECK")
                        || st.containsPhrase("WITHOUT", "AUTHORITY-CHECK")) {
                    if (st.containsPhrase("WITHOUT", "AUTHORITY-CHECK")) {
                        findings.add(findingAt(st, getRuleId(), getCategory(), RuleSeverity.CRITICAL)
                                .confidence(0.95)
                                .title("CALL TRANSACTION explicitly skips the authority check")
                                .explanation("WITHOUT AUTHORITY-CHECK bypasses the S_TCODE authorization "
                                        + "check entirely.")
                                .recommendation("Use CALL TRANSACTION ... WITH AUTHORITY-CHECK and handle "
                                        + "CX_SY_AUTHORIZATION_ERROR.")
                                .addDocumentationReference("SAP Help: CALL TRANSACTION and authorization")
                                .build());
                    }
                    continue;
                }
                findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                        .confidence(0.85)
                        .title("CALL TRANSACTION without WITH AUTHORITY-CHECK")
                        .explanation("Without the WITH AUTHORITY-CHECK addition, no S_TCODE authorization "
                                + "check is performed for the called transaction.")
                        .recommendation("Add WITH AUTHORITY-CHECK and handle CX_SY_AUTHORIZATION_ERROR, or "
                                + "perform an explicit AUTHORITY-CHECK before the call.")
                        .addDocumentationReference("SAP Help: CALL TRANSACTION and authorization")
                        .build());
            }
            return findings;
        }
    }

    static final class OsCommand extends AbstractRule {
        OsCommand() {
            super("SEC_OS_COMMAND", RuleCategory.SECURITY, RuleSeverity.CRITICAL);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                // CALL 'SYSTEM': the literal must be the token directly after CALL,
                // so 'SYSTEM' inside an unrelated string never matches.
                AbapToken afterCall = "CALL".equals(st.getFirstKeyword())
                        ? st.tokenAfterWord("CALL") : null;
                boolean callSystem = afterCall != null
                        && "'SYSTEM'".equals(afterCall.getUpperText());
                // SXPG only via CALL FUNCTION '<name>' — the function name literal.
                boolean sxpg = callsFunction(st, "SXPG_COMMAND_EXECUTE")
                        || callsFunction(st, "SXPG_CALL_SYSTEM");
                boolean filter = "OPEN".equals(st.getFirstKeyword()) && st.containsWord("FILTER");
                if (callSystem || sxpg || filter) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(sxpg ? 0.8 : 0.9)
                            .requiresHumanReview(true)
                            .title("Operating system command execution")
                            .explanation("Executing OS commands from ABAP is dangerous, especially when any "
                                    + "part of the command or its parameters can be influenced by input "
                                    + "(command injection).")
                            .recommendation("Prefer SAP-managed external commands (SM69) with fixed command "
                                    + "definitions and validated parameters; avoid CALL 'SYSTEM' and "
                                    + "OPEN DATASET ... FILTER entirely.")
                            .addDocumentationReference("CWE-78: OS Command Injection")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class UnsafeDatasetPath extends AbstractRule {
        UnsafeDatasetPath() {
            super("SEC_UNSAFE_DATASET_PATH", RuleCategory.SECURITY, RuleSeverity.HIGH);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                if (!"OPEN".equals(st.getFirstKeyword()) || !st.containsWord("DATASET")) {
                    continue;
                }
                AbapToken pathToken = st.tokenAfterWord("DATASET");
                boolean literalPath = pathToken != null && (pathToken.getType() == TokenType.STRING_LITERAL
                        || pathToken.getType() == TokenType.BACKQUOTE_LITERAL);
                boolean validated = validatedNearby(context, st);
                if (!literalPath && !validated) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.65)
                            .requiresHumanReview(true)
                            .title("OPEN DATASET with unvalidated file path")
                            .explanation("A variable file path used with OPEN DATASET can be abused for "
                                    + "directory traversal if it is derived from user input.")
                            .recommendation("Validate the path with CL_FS_PATH / FILE_VALIDATE_NAME, use "
                                    + "logical file names (FILE transaction), and perform an "
                                    + "AUTHORITY-CHECK for S_DATASET / S_PATH.")
                            .addDocumentationReference("CWE-22: Path Traversal")
                            .build());
                }
            }
            return findings;
        }

        private boolean validatedNearby(AnalysisContext context, AbapStatement open) {
            for (AbapStatement st : context.getSource().statementsBefore(open)) {
                if (st.containsWord("CL_FS_PATH")
                        || callsFunction(st, "FILE_VALIDATE_NAME")
                        || callsFunction(st, "FILE_GET_NAME")
                        || ("AUTHORITY-CHECK".equals(st.getFirstKeyword())
                        && st.literalTokens().stream().anyMatch(t ->
                        t.getUpperText().contains("S_DATASET") || t.getUpperText().contains("S_PATH")))) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class UnvalidatedHttpTarget extends AbstractRule {
        UnvalidatedHttpTarget() {
            super("SEC_UNVALIDATED_HTTP_TARGET", RuleCategory.SECURITY, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                boolean createByUrl = st.containsWord("CL_HTTP_CLIENT")
                        && codeContains(st, "CREATE_BY_URL");
                if (!createByUrl) {
                    continue;
                }
                boolean literalUrl = st.literalTokens().stream()
                        .anyMatch(t -> t.getUpperText().contains("HTTP"));
                if (!literalUrl) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.6)
                            .requiresHumanReview(true)
                            .title("HTTP client created from a variable URL")
                            .explanation("If the target URL is derived from input, requests can be redirected "
                                    + "to attacker-controlled hosts (SSRF) or leak data to unintended "
                                    + "destinations.")
                            .recommendation("Validate the URL against an allow-list of approved destinations, "
                                    + "or use configured RFC/HTTP destinations (SM59) via "
                                    + "cl_http_client=>create_by_destination.")
                            .addDocumentationReference("CWE-918: Server-Side Request Forgery")
                            .build());
                }
            }
            return findings;
        }
    }

    /**
     * Heuristic indicator only: reports that no AUTHORITY-CHECK statement was
     * detected in a source unit that performs sensitive operations. It never
     * claims an authorization check is missing – checks may exist in callers,
     * called modules or the framework.
     */
    static final class AuthorizationCheckIndicator extends AbstractRule {
        AuthorizationCheckIndicator() {
            super("SEC_AUTHORIZATION_CHECK_INDICATOR", RuleCategory.SECURITY, RuleSeverity.LOW);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            boolean hasAuthorityCheck = context.getSource().getStatements().stream()
                    .anyMatch(st -> "AUTHORITY-CHECK".equals(st.getFirstKeyword())
                            || st.containsWord("AUTHORITY-CHECK"));
            if (hasAuthorityCheck) {
                return List.of();
            }
            AbapStatement sensitive = null;
            for (AbapStatement st : context.getSource().getStatements()) {
                if ("CALL TRANSACTION".equals(st.getLeadingWords(2))
                        || "SUBMIT".equals(st.getFirstKeyword())
                        || isDatabaseChange(st)
                        || (isSelect(st) && context.getConfiguration()
                        .isSensitiveTable(com.abapguardian.core.rules.RuleSupport.selectTable(st)))) {
                    sensitive = st;
                    break;
                }
            }
            if (sensitive == null) {
                return List.of();
            }
            return List.of(findingAt(sensitive, getRuleId(), getCategory(), getDefaultSeverity())
                    .confidence(0.3)
                    .requiresHumanReview(true)
                    .title("Heuristic: no AUTHORITY-CHECK detected in this source unit")
                    .explanation("This source unit performs sensitive operations, and no AUTHORITY-CHECK "
                            + "statement was detected within the analyzed code. THIS IS A HEURISTIC "
                            + "INDICATOR ONLY: an authorization check may well exist in the caller, in "
                            + "invoked modules, or be enforced by the surrounding framework. This finding "
                            + "does not state that an authorization check is missing.")
                    .recommendation("Verify where authorization for this operation is enforced and document "
                            + "it. If no check exists anywhere on the call path, add an AUTHORITY-CHECK.")
                    .addDocumentationReference("SAP Help: AUTHORITY-CHECK")
                    .build());
        }
    }

    static final class ClientSpecified extends AbstractRule {
        ClientSpecified() {
            super("SEC_CLIENT_SPECIFIED", RuleCategory.SECURITY, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            for (AbapStatement st : context.getSource().getStatements()) {
                boolean clientSpecified = st.containsPhrase("CLIENT", "SPECIFIED")
                        || st.containsPhrase("USING", "CLIENT")
                        || st.containsPhrase("CLIENTS", "SPECIFIED");
                if (clientSpecified) {
                    findings.add(findingAt(st, getRuleId(), getCategory(), getDefaultSeverity())
                            .confidence(0.9)
                            .requiresHumanReview(true)
                            .title("Cross-client data access (CLIENT SPECIFIED / USING CLIENT)")
                            .explanation("Bypassing automatic client handling can expose data of other "
                                    + "clients and violate tenant isolation.")
                            .recommendation("Avoid cross-client access in application code. If it is "
                                    + "genuinely required (system tooling), restrict and audit it.")
                            .addDocumentationReference("SAP Help: client handling in ABAP SQL")
                            .build());
                }
            }
            return findings;
        }
    }

    static final class MissingSySubrcHandling extends AbstractRule {
        MissingSySubrcHandling() {
            super("SEC_MISSING_SY_SUBRC_HANDLING", RuleCategory.SECURITY, RuleSeverity.MEDIUM);
        }

        @Override
        public List<Finding> analyze(AnalysisContext context) {
            List<Finding> findings = new ArrayList<>();
            List<AbapStatement> statements = context.getSource().getStatements();
            for (AbapStatement st : statements) {
                boolean relevant = "AUTHORITY-CHECK".equals(st.getFirstKeyword())
                        || (isSelect(st) && st.containsWord("SINGLE"))
                        || ("OPEN".equals(st.getFirstKeyword()) && st.containsWord("DATASET"))
                        || ("CALL FUNCTION".equals(st.getLeadingWords(2)) && st.containsWord("EXCEPTIONS"));
                if (!relevant) {
                    continue;
                }
                if (!subrcCheckedSoon(context, st)) {
                    boolean auth = "AUTHORITY-CHECK".equals(st.getFirstKeyword());
                    findings.add(findingAt(st, getRuleId(), getCategory(),
                            auth ? RuleSeverity.HIGH : getDefaultSeverity())
                            .confidence(auth ? 0.85 : 0.6)
                            .requiresHumanReview(!auth)
                            .title(auth ? "AUTHORITY-CHECK result (sy-subrc) not evaluated"
                                    : "sy-subrc not evaluated after a statement that sets it")
                            .explanation(auth
                                    ? "An AUTHORITY-CHECK whose sy-subrc result is not evaluated has no "
                                    + "effect: the program continues even when the user lacks authorization."
                                    : "The statement sets sy-subrc to signal success or failure, but the "
                                    + "result is not checked before the next operation.")
                            .recommendation("Evaluate sy-subrc immediately after the statement and handle "
                                    + "the failure path explicitly.")
                            .addDocumentationReference("SAP Help: sy-subrc handling")
                            .build());
                }
            }
            return findings;
        }

        private boolean subrcCheckedSoon(AnalysisContext context, AbapStatement st) {
            List<AbapStatement> after = context.getSource().statementsAfter(st);
            int checked = 0;
            for (AbapStatement next : after) {
                if (checked >= 3) {
                    return false;
                }
                if (next.containsWord("SY-SUBRC")) {
                    return true;
                }
                String kw = next.getFirstKeyword();
                // Structural statements don't consume the sy-subrc "window".
                if (kw.startsWith("END") || "ELSE".equals(kw)) {
                    return false;
                }
                checked++;
            }
            return false;
        }
    }
}
