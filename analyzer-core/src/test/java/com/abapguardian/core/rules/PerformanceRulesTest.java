package com.abapguardian.core.rules;

import com.abapguardian.core.config.RuleConfiguration;
import com.abapguardian.core.engine.RuleEngine;
import com.abapguardian.core.rule.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceRulesTest {

    private List<Finding> findings(String code) {
        return new RuleEngine(RuleRegistry.allRules(), RuleConfiguration.defaults())
                .analyze(code, "ZTEST", "PROG").findings();
    }

    private boolean has(List<Finding> findings, String ruleId) {
        return findings.stream().anyMatch(f -> f.getRuleId().equals(ruleId));
    }

    @Test
    void databaseChangeInLoop() {
        String code = """
                LOOP AT lt_data INTO ls_data.
                  UPDATE ztable SET field = ls_data-field WHERE key = ls_data-key.
                ENDLOOP.
                """;
        assertTrue(has(findings(code), "PERF_DATABASE_CHANGE_IN_LOOP"));
    }

    @Test
    void internalTableOperationsInLoopAreNotDatabaseChanges() {
        String code = """
                LOOP AT lt_data INTO ls_data.
                  INSERT ls_data INTO TABLE lt_result.
                  DELETE lt_temp INDEX 1.
                  MODIFY lt_data FROM ls_data INDEX sy-tabix TRANSPORTING field.
                ENDLOOP.
                """;
        assertFalse(has(findings(code), "PERF_DATABASE_CHANGE_IN_LOOP"));
    }

    @Test
    void rfcInLoop() {
        String code = """
                LOOP AT lt_items INTO ls_item.
                  CALL FUNCTION 'Z_REMOTE_UPDATE' DESTINATION lv_dest
                    EXPORTING iv_item = ls_item.
                ENDLOOP.
                """;
        assertTrue(has(findings(code), "PERF_RFC_OR_FUNCTION_IN_LOOP"));
    }

    @Test
    void selectEndselectDetected() {
        String code = """
                SELECT pernr FROM pa0002 INTO lv_pernr WHERE begda > lv_date.
                  WRITE lv_pernr.
                ENDSELECT.
                """;
        assertTrue(has(findings(code), "PERF_SELECT_ENDSELECT"));
    }

    @Test
    void nestedLoopDetected() {
        String code = """
                LOOP AT lt_outer INTO ls_outer.
                  LOOP AT lt_inner INTO ls_inner.
                    WRITE ls_inner-f.
                  ENDLOOP.
                ENDLOOP.
                """;
        assertTrue(has(findings(code), "PERF_NESTED_STANDARD_TABLE_LOOP"));
    }

    @Test
    void nestedLoopWithSecondaryKeyIsAccepted() {
        String code = """
                LOOP AT lt_outer INTO ls_outer.
                  LOOP AT lt_inner INTO ls_inner USING KEY sorted_key WHERE id = ls_outer-id.
                    WRITE ls_inner-f.
                  ENDLOOP.
                ENDLOOP.
                """;
        assertFalse(has(findings(code), "PERF_NESTED_STANDARD_TABLE_LOOP"));
    }

    @Test
    void sortAndCommitInLoop() {
        String code = """
                WHILE lv_go = abap_true.
                  SORT lt_data BY key.
                  COMMIT WORK.
                ENDWHILE.
                """;
        List<Finding> f = findings(code);
        assertTrue(has(f, "PERF_REPEATED_SORT_IN_LOOP"));
        assertTrue(has(f, "PERF_COMMIT_IN_LOOP"));
    }

    @Test
    void repeatedReadTableWithKeyInLoop() {
        String code = """
                LOOP AT lt_orders INTO ls_order.
                  READ TABLE lt_customers INTO ls_customer WITH KEY id = ls_order-customer_id.
                ENDLOOP.
                """;
        assertTrue(has(findings(code), "PERF_REPEATED_READ_TABLE"));
    }

    @Test
    void readTableWithBinarySearchIsAccepted() {
        String code = """
                LOOP AT lt_orders INTO ls_order.
                  READ TABLE lt_customers INTO ls_customer WITH KEY id = ls_order-customer_id BINARY SEARCH.
                ENDLOOP.
                """;
        assertFalse(has(findings(code), "PERF_REPEATED_READ_TABLE"));
    }

    @Test
    void unboundedSelectIntoTable() {
        String code = "SELECT matnr FROM mara INTO TABLE lt_mara.";
        assertTrue(has(findings(code), "PERF_UNBOUNDED_INTERNAL_TABLE"));
    }

    @Test
    void dynamicSqlDetected() {
        String code = "SELECT * FROM (lv_table) INTO TABLE <fs_table> WHERE (lv_where).";
        List<Finding> f = findings(code);
        assertTrue(has(f, "PERF_DYNAMIC_SQL"));
        assertTrue(has(f, "SEC_DYNAMIC_SQL_INPUT"));
    }

    @Test
    void unusedSelectedFieldsDetected() {
        String code = """
                SELECT pernr nachn vorna gbdat FROM pa0002 INTO ls_emp WHERE pernr = lv_pernr.
                WRITE ls_emp-pernr.
                WRITE ls_emp-nachn.
                """;
        List<Finding> f = findings(code);
        Finding unused = f.stream().filter(x -> x.getRuleId().equals("PERF_UNUSED_SELECTED_FIELDS"))
                .findFirst().orElse(null);
        assertTrue(unused != null && unused.getTitle().contains("VORNA")
                && unused.getTitle().contains("GBDAT"));
    }

    @Test
    void selectWithoutWhereDetected() {
        String code = "SELECT matnr FROM mara INTO TABLE lt_mara.";
        assertTrue(has(findings(code), "PERF_SELECT_WITHOUT_WHERE"));
    }

    @Test
    void selectWithUpToRowsAccepted() {
        String code = "SELECT matnr FROM mara INTO TABLE lt_mara UP TO 100 ROWS.";
        assertFalse(has(findings(code), "PERF_SELECT_WITHOUT_WHERE"));
    }
}
