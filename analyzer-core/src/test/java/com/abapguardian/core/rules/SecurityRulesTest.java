package com.abapguardian.core.rules;

import com.abapguardian.core.config.RuleConfiguration;
import com.abapguardian.core.engine.RuleEngine;
import com.abapguardian.core.rule.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityRulesTest {

    private List<Finding> findings(String code) {
        return new RuleEngine(RuleRegistry.allRules(), RuleConfiguration.defaults())
                .analyze(code, "ZTEST", "PROG").findings();
    }

    private boolean has(List<Finding> findings, String ruleId) {
        return findings.stream().anyMatch(f -> f.getRuleId().equals(ruleId));
    }

    @Test
    void hardcodedPassword() {
        assertTrue(has(findings("lv_password = 'hunter2'."), "SEC_HARDCODED_PASSWORD"));
    }

    @Test
    void emptyPasswordLiteralIsNotFlagged() {
        assertFalse(has(findings("lv_password = ''."), "SEC_HARDCODED_PASSWORD"));
    }

    @Test
    void hardcodedToken() {
        assertTrue(has(findings("lv_api_key = 'sk-abcdefabcdefabcdefabcdef1234'."),
                "SEC_HARDCODED_TOKEN"));
        assertTrue(has(findings("lv_header = 'Bearer eyJhbGciOiJIUzI1NiJ9'."),
                "SEC_HARDCODED_TOKEN"));
    }

    @Test
    void unsafeCallTransaction() {
        assertTrue(has(findings("CALL TRANSACTION 'SU01'."), "SEC_UNSAFE_CALL_TRANSACTION"));
        assertFalse(has(findings("CALL TRANSACTION 'SU01' WITH AUTHORITY-CHECK."),
                "SEC_UNSAFE_CALL_TRANSACTION"));
        assertTrue(has(findings("CALL TRANSACTION 'SU01' WITHOUT AUTHORITY-CHECK."),
                "SEC_UNSAFE_CALL_TRANSACTION"));
    }

    @Test
    void dynamicAbapGeneration() {
        assertTrue(has(findings("GENERATE SUBROUTINE POOL lt_code NAME lv_prog."),
                "SEC_DYNAMIC_ABAP_GENERATION"));
        assertTrue(has(findings("INSERT REPORT lv_name FROM lt_code."),
                "SEC_DYNAMIC_ABAP_GENERATION"));
    }

    @Test
    void osCommand() {
        assertTrue(has(findings("CALL 'SYSTEM' ID 'COMMAND' FIELD lv_cmd."), "SEC_OS_COMMAND"));
        assertTrue(has(findings("CALL FUNCTION 'SXPG_COMMAND_EXECUTE' EXPORTING commandname = lv_cmd."),
                "SEC_OS_COMMAND"));
    }

    @Test
    void osCommandKeywordsInsideLiteralsAreNotFlagged() {
        assertFalse(has(findings("WRITE 'Docs about SXPG_COMMAND_EXECUTE usage'."), "SEC_OS_COMMAND"));
        assertFalse(has(findings("lv_note = 'the SYSTEM call is documented here'."), "SEC_OS_COMMAND"));
        assertFalse(has(findings("CALL FUNCTION 'Z_MY_FUNC' EXPORTING txt = 'SXPG_CALL_SYSTEM'."),
                "SEC_OS_COMMAND"));
        assertFalse(has(findings("WRITE 'cl_http_client=>create_by_url example'."),
                "SEC_UNVALIDATED_HTTP_TARGET"));
    }

    @Test
    void unsafeDatasetPath() {
        assertTrue(has(findings("OPEN DATASET lv_path FOR OUTPUT IN TEXT MODE ENCODING DEFAULT."),
                "SEC_UNSAFE_DATASET_PATH"));
    }

    @Test
    void clientSpecified() {
        assertTrue(has(findings("SELECT * FROM t000 CLIENT SPECIFIED INTO TABLE lt WHERE mandt = '000'."),
                "SEC_CLIENT_SPECIFIED"));
    }

    @Test
    void authorityCheckWithoutSubrcEvaluation() {
        String code = """
                AUTHORITY-CHECK OBJECT 'S_TCODE' ID 'TCD' FIELD 'SU01'.
                WRITE 'continuing regardless'.
                """;
        assertTrue(has(findings(code), "SEC_MISSING_SY_SUBRC_HANDLING"));
    }

    @Test
    void authorityCheckWithSubrcEvaluationIsAccepted() {
        String code = """
                AUTHORITY-CHECK OBJECT 'S_TCODE' ID 'TCD' FIELD 'SU01'.
                IF sy-subrc <> 0.
                  MESSAGE e001(zz).
                ENDIF.
                """;
        assertFalse(has(findings(code), "SEC_MISSING_SY_SUBRC_HANDLING"));
    }

    @Test
    void authorizationIndicatorIsHeuristicAndCautious() {
        String code = "CALL TRANSACTION 'SU01' WITH AUTHORITY-CHECK.";
        List<Finding> f = findings(code);
        Finding indicator = f.stream()
                .filter(x -> x.getRuleId().equals("SEC_AUTHORIZATION_CHECK_INDICATOR"))
                .findFirst().orElse(null);
        if (indicator != null) {
            assertTrue(indicator.isRequiresHumanReview());
            assertTrue(indicator.getExplanation().contains("HEURISTIC"));
            assertTrue(indicator.getConfidence() < 0.5);
            assertFalse(indicator.getTitle().toLowerCase().contains("missing authorization"));
        }
        // When an AUTHORITY-CHECK exists in the source, the indicator must not fire.
        String withCheck = code + "\nAUTHORITY-CHECK OBJECT 'S_TCODE' ID 'TCD' FIELD 'SU01'.\n"
                + "IF sy-subrc <> 0. RETURN. ENDIF.";
        assertFalse(has(findings(withCheck), "SEC_AUTHORIZATION_CHECK_INDICATOR"));
    }
}
