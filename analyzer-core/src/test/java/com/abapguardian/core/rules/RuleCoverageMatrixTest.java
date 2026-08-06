package com.abapguardian.core.rules;

import com.abapguardian.core.config.RuleConfiguration;
import com.abapguardian.core.engine.RuleEngine;
import com.abapguardian.core.rule.AbapRule;
import com.abapguardian.core.rule.Finding;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-rule test matrix: every registered rule must
 * <ul>
 *   <li>fire on a minimal bad-code snippet (positive test), and</li>
 *   <li>stay silent when its trigger keywords appear only inside a comment
 *       and a string literal (false-positive test).</li>
 * </ul>
 * The matrix is checked for completeness against {@link RuleRegistry}, so a
 * newly registered rule without matrix entries fails the build.
 */
class RuleCoverageMatrixTest {

    private static final class Case {
        final String positive;
        final String negative;

        Case(String positive, String negative) {
            this.positive = positive;
            this.negative = negative;
        }
    }

    /**
     * Wraps the given trigger keywords into a snippet where they occur only
     * inside a full-line comment, an inline comment and a string literal.
     */
    private static String keywordsInCommentAndLiteral(String keywords) {
        return "* " + keywords + "\n"
                + "DATA lv_doc TYPE string. \" " + keywords + "\n"
                + "lv_doc = '" + keywords + "'.\n";
    }

    private static final Map<String, Case> MATRIX = new LinkedHashMap<>();

    private static void row(String ruleId, String positive, String negativeKeywords) {
        MATRIX.put(ruleId, new Case(positive, keywordsInCommentAndLiteral(negativeKeywords)));
    }

    static {
        // ---------- Performance ----------
        row("PERF_SELECT_IN_LOOP", """
                LOOP AT lt_items INTO ls_item.
                  SELECT SINGLE matnr FROM mara INTO lv_matnr WHERE matnr = ls_item-matnr.
                ENDLOOP.
                """,
                "LOOP AT lt_items. SELECT SINGLE matnr FROM mara. ENDLOOP.");
        row("PERF_DATABASE_CHANGE_IN_LOOP", """
                LOOP AT lt_data INTO ls_data.
                  UPDATE ztable SET field = ls_data-field WHERE key = ls_data-key.
                ENDLOOP.
                """,
                "LOOP AT lt_data. UPDATE ztable SET field. ENDLOOP.");
        row("PERF_RFC_OR_FUNCTION_IN_LOOP", """
                LOOP AT lt_items INTO ls_item.
                  CALL FUNCTION 'Z_REMOTE_UPDATE' DESTINATION lv_dest
                    EXPORTING iv_item = ls_item.
                ENDLOOP.
                """,
                "LOOP AT lt. CALL FUNCTION Z_REMOTE_UPDATE DESTINATION. ENDLOOP.");
        row("PERF_SELECT_STAR",
                "SELECT * FROM mara INTO TABLE lt_mara WHERE matnr = lv_matnr.",
                "SELECT * FROM mara");
        row("PERF_SELECT_WITHOUT_WHERE",
                "SELECT matnr FROM mara INTO TABLE lt_mara.",
                "SELECT matnr FROM mara INTO TABLE lt_mara.");
        row("PERF_FOR_ALL_ENTRIES_WITHOUT_EMPTY_CHECK", """
                SELECT matnr FROM mara INTO TABLE lt_res
                  FOR ALL ENTRIES IN lt_keys WHERE matnr = lt_keys-matnr.
                """,
                "SELECT matnr FROM mara FOR ALL ENTRIES IN lt_keys WHERE matnr");
        row("PERF_NESTED_STANDARD_TABLE_LOOP", """
                LOOP AT lt_outer INTO ls_outer.
                  LOOP AT lt_inner INTO ls_inner.
                    WRITE ls_inner-f.
                  ENDLOOP.
                ENDLOOP.
                """,
                "LOOP AT lt_outer. LOOP AT lt_inner. ENDLOOP. ENDLOOP.");
        row("PERF_REPEATED_SORT_IN_LOOP", """
                WHILE lv_go = abap_true.
                  SORT lt_data BY key.
                ENDWHILE.
                """,
                "WHILE go. SORT lt_data BY key. ENDWHILE.");
        row("PERF_COMMIT_IN_LOOP", """
                WHILE lv_go = abap_true.
                  COMMIT WORK.
                ENDWHILE.
                """,
                "DO 10 TIMES. COMMIT WORK. ENDDO.");
        row("PERF_SELECT_ENDSELECT", """
                SELECT pernr FROM pa0002 INTO lv_pernr WHERE begda > lv_date.
                  WRITE lv_pernr.
                ENDSELECT.
                """,
                "SELECT pernr FROM pa0002. ENDSELECT.");
        row("PERF_REPEATED_READ_TABLE", """
                LOOP AT lt_orders INTO ls_order.
                  READ TABLE lt_customers INTO ls_customer WITH KEY id = ls_order-customer_id.
                ENDLOOP.
                """,
                "LOOP AT lt. READ TABLE lt_customers WITH KEY id. ENDLOOP.");
        row("PERF_UNBOUNDED_INTERNAL_TABLE",
                "SELECT matnr FROM mara INTO TABLE lt_mara.",
                "SELECT matnr FROM mara INTO TABLE lt_mara.");
        row("PERF_DYNAMIC_SQL",
                "SELECT * FROM (lv_table) INTO TABLE <fs_table> WHERE (lv_where).",
                "SELECT * FROM (lv_table) WHERE (lv_where).");
        row("PERF_UNUSED_SELECTED_FIELDS", """
                SELECT pernr nachn vorna gbdat FROM pa0002 INTO ls_emp WHERE pernr = lv_pernr.
                WRITE ls_emp-pernr.
                WRITE ls_emp-nachn.
                """,
                "SELECT pernr nachn FROM pa0002 INTO ls_emp.");

        // ---------- Security ----------
        row("SEC_HARDCODED_PASSWORD",
                "lv_password = 'hunter2'.",
                "the password = secret literal is documented here");
        row("SEC_HARDCODED_TOKEN",
                "lv_api_key = 'sk-abcdefabcdefabcdefabcdef1234'.",
                "token api_key secret notes for the reader");
        row("SEC_DYNAMIC_SQL_INPUT",
                "SELECT * FROM (lv_table) INTO TABLE <fs_table> WHERE (lv_where).",
                "EXEC SQL and CL_SQL_STATEMENT are described here");
        row("SEC_DYNAMIC_ABAP_GENERATION",
                "GENERATE SUBROUTINE POOL lt_code NAME lv_prog.",
                "GENERATE SUBROUTINE POOL and INSERT REPORT docs");
        row("SEC_UNSAFE_CALL_TRANSACTION",
                "CALL TRANSACTION 'SU01'.",
                "CALL TRANSACTION SU01 is explained in this text");
        row("SEC_OS_COMMAND",
                "CALL FUNCTION 'SXPG_COMMAND_EXECUTE' EXPORTING commandname = lv_cmd.",
                "docs about SXPG_COMMAND_EXECUTE and SXPG_CALL_SYSTEM and SYSTEM");
        row("SEC_UNSAFE_DATASET_PATH",
                "OPEN DATASET lv_path FOR OUTPUT IN TEXT MODE ENCODING DEFAULT.",
                "OPEN DATASET lv_path FOR OUTPUT notes");
        row("SEC_UNVALIDATED_HTTP_TARGET",
                "cl_http_client=>create_by_url( EXPORTING url = lv_url IMPORTING client = lo_client ).",
                "cl_http_client=>create_by_url example call");
        row("SEC_AUTHORIZATION_CHECK_INDICATOR",
                "UPDATE ztable SET field = 1 WHERE key = 1.",
                "UPDATE ztable SET field and CALL TRANSACTION and SUBMIT");
        row("SEC_CLIENT_SPECIFIED",
                "SELECT * FROM t000 CLIENT SPECIFIED INTO TABLE lt WHERE mandt = '000'.",
                "SELECT FROM t000 CLIENT SPECIFIED and USING CLIENT docs");
        row("SEC_MISSING_SY_SUBRC_HANDLING", """
                AUTHORITY-CHECK OBJECT 'S_TCODE' ID 'TCD' FIELD 'SU01'.
                WRITE 'continuing regardless'.
                """,
                "AUTHORITY-CHECK OBJECT S_TCODE without sy-subrc handling");

        // ---------- Privacy ----------
        row("PRIV_PERSONAL_DATA_IN_LOG",
                "CALL FUNCTION 'BAL_LOG_MSG_ADD' EXPORTING i_msg = ls_p-nachn.",
                "BAL_LOG_MSG_ADD logs pernr nachn vorna values");
        row("PRIV_PERSONAL_DATA_IN_MESSAGE",
                "MESSAGE i001(zz) WITH ls_p-nachn ls_p-vorna.",
                "MESSAGE WITH ls_p-nachn ls_p-vorna sample");
        row("PRIV_PERSONAL_DATA_IN_SPOOL",
                "WRITE: / ls_p-pernr, ls_p-nachn.",
                "WRITE ls_p-pernr ls_p-nachn to the list");
        row("PRIV_PERSONAL_DATA_IN_FILE_EXPORT",
                "TRANSFER ls_p-nachn TO lv_file.",
                "TRANSFER ls_p-nachn TO lv_file docs");
        row("PRIV_BROAD_HR_MASTER_DATA_SELECTION",
                "SELECT * FROM pa0002 INTO TABLE lt_p WHERE pernr = lv_pernr.",
                "SELECT * FROM pa0002 full table read");
        row("PRIV_UNMASKED_PERSONNEL_NUMBER",
                "WRITE lv_pernr.",
                "WRITE lv_pernr without masking");
        row("PRIV_EXTERNAL_DATA_TRANSFER", """
                SELECT pernr nachn FROM pa0002 INTO TABLE lt_p WHERE pernr = lv_pernr.
                CALL FUNCTION 'Z_SEND_DATA' DESTINATION 'EXTERNAL_SYS' EXPORTING it_data = lt_p.
                """,
                "CALL FUNCTION Z_SEND_DATA DESTINATION EXTERNAL_SYS with pernr nachn");
        row("PRIV_EXCESSIVE_FIELD_SELECTION",
                "SELECT pernr nachn vorna gbdat stras FROM pa0002 INTO TABLE lt WHERE pernr = lv.",
                "SELECT pernr nachn vorna gbdat stras FROM pa0002");
        row("PRIV_DEBUG_OUTPUT_OF_PERSONAL_DATA", """
                WRITE ls_p-nachn.
                BREAK-POINT.
                """,
                "BREAK-POINT near pernr nachn CL_DEMO_OUTPUT");
    }

    private List<Finding> findings(String code) {
        return new RuleEngine(RuleRegistry.allRules(), RuleConfiguration.defaults())
                .analyze(code, "ZTEST", "PROG").findings();
    }

    private boolean has(List<Finding> findings, String ruleId) {
        return findings.stream().anyMatch(f -> f.getRuleId().equals(ruleId));
    }

    @Test
    void matrixCoversEveryRegisteredRuleExactly() {
        Set<String> registered = RuleRegistry.allRules().stream()
                .map(AbapRule::getRuleId)
                .collect(Collectors.toSet());
        assertEquals(registered, MATRIX.keySet(),
                "Every registered rule needs a positive and a false-positive matrix entry");
    }

    @TestFactory
    List<DynamicTest> everyRuleFiresOnItsBadCodeSnippet() {
        List<DynamicTest> tests = new ArrayList<>();
        MATRIX.forEach((ruleId, c) -> tests.add(DynamicTest.dynamicTest(
                ruleId + " fires on bad code",
                () -> assertTrue(has(findings(c.positive), ruleId),
                        ruleId + " should fire on:\n" + c.positive))));
        return tests;
    }

    @TestFactory
    List<DynamicTest> everyRuleIgnoresKeywordsInCommentsAndLiterals() {
        List<DynamicTest> tests = new ArrayList<>();
        MATRIX.forEach((ruleId, c) -> tests.add(DynamicTest.dynamicTest(
                ruleId + " ignores comments/literals",
                () -> assertFalse(has(findings(c.negative), ruleId),
                        ruleId + " must not fire on:\n" + c.negative))));
        return tests;
    }
}
