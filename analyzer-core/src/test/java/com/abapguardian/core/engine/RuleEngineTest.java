package com.abapguardian.core.engine;

import com.abapguardian.core.config.RuleConfiguration;
import com.abapguardian.core.rule.Finding;
import com.abapguardian.core.rule.RuleSeverity;
import com.abapguardian.core.rules.RuleRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {

    private RuleEngine engine() {
        return new RuleEngine(RuleRegistry.allRules(), RuleConfiguration.defaults());
    }

    private List<Finding> findings(String code) {
        return engine().analyze(code, "ZTEST", "PROG").findings();
    }

    private boolean has(List<Finding> findings, String ruleId) {
        return findings.stream().anyMatch(f -> f.getRuleId().equals(ruleId));
    }

    @Test
    void detectsExpectedFindingsInSampleBadCode() {
        String code = """
                LOOP AT lt_person INTO DATA(ls_person).
                  SELECT SINGLE *
                    FROM pa0002
                    INTO @DATA(ls_pa0002)
                    WHERE pernr = @ls_person-pernr.

                  WRITE: / ls_pa0002-pernr,
                           ls_pa0002-nachn,
                           ls_pa0002-vorna.
                ENDLOOP.
                """;
        List<Finding> result = findings(code);
        assertTrue(has(result, "PERF_SELECT_IN_LOOP"), "expected PERF_SELECT_IN_LOOP");
        assertTrue(has(result, "PERF_SELECT_STAR"), "expected PERF_SELECT_STAR");
        assertTrue(has(result, "PRIV_PERSONAL_DATA_IN_SPOOL"), "expected PRIV_PERSONAL_DATA_IN_SPOOL");
        assertTrue(has(result, "PRIV_BROAD_HR_MASTER_DATA_SELECTION"),
                "expected PRIV_BROAD_HR_MASTER_DATA_SELECTION");
    }

    @Test
    void findingLineNumbersAreAccurate() {
        String code = """
                DATA lv TYPE i.
                LOOP AT lt INTO ls.
                  SELECT SINGLE * FROM pa0002 INTO ls_p WHERE pernr = ls-pernr.
                ENDLOOP.
                """;
        List<Finding> result = findings(code);
        Finding selectInLoop = result.stream()
                .filter(f -> f.getRuleId().equals("PERF_SELECT_IN_LOOP")).findFirst().orElseThrow();
        assertEquals(3, selectInLoop.getStartLine());
        assertEquals(3, selectInLoop.getStartColumn());
    }

    @Test
    void commentsDoNotCreateFalseFindings() {
        String code = """
                * LOOP AT lt INTO ls.
                *   SELECT SINGLE * FROM pa0002 INTO ls_p.
                * ENDLOOP.
                WRITE 'hello'. " SELECT * FROM pa0002 WHERE pernr = 1
                """;
        List<Finding> result = findings(code);
        assertFalse(has(result, "PERF_SELECT_IN_LOOP"));
        assertFalse(has(result, "PERF_SELECT_STAR"));
        assertFalse(has(result, "PRIV_BROAD_HR_MASTER_DATA_SELECTION"));
    }

    @Test
    void stringLiteralsWithKeywordsAreIgnored() {
        String code = """
                lv_text = 'SELECT * FROM pa0002 WHERE pernr = 1'.
                lv_tmpl = `LOOP AT lt_person`.
                WRITE 'COMMIT WORK'.
                """;
        List<Finding> result = findings(code);
        assertFalse(has(result, "PERF_SELECT_STAR"));
        assertFalse(has(result, "PERF_SELECT_IN_LOOP"));
        assertFalse(has(result, "PERF_COMMIT_IN_LOOP"));
    }

    @Test
    void forAllEntriesWithoutEmptyCheckIsDetected() {
        String code = """
                SELECT pernr nachn FROM pa0002
                  INTO TABLE lt_result
                  FOR ALL ENTRIES IN lt_driver
                  WHERE pernr = lt_driver-pernr.
                """;
        assertTrue(has(findings(code), "PERF_FOR_ALL_ENTRIES_WITHOUT_EMPTY_CHECK"));
    }

    @Test
    void forAllEntriesWithEmptyCheckIsAccepted() {
        String code = """
                IF lt_driver IS NOT INITIAL.
                  SELECT pernr nachn FROM pa0002
                    INTO TABLE lt_result
                    FOR ALL ENTRIES IN lt_driver
                    WHERE pernr = lt_driver-pernr.
                ENDIF.
                """;
        assertFalse(has(findings(code), "PERF_FOR_ALL_ENTRIES_WITHOUT_EMPTY_CHECK"));
    }

    @Test
    void suppressionWithReasonSuppresses() {
        String code = """
                WRITE ls_p-pernr. "#EC ABAP_GUARDIAN: PRIV_PERSONAL_DATA_IN_SPOOL reason="Approved protected audit log"
                """;
        AnalysisResult result = engine().analyze(code, "ZTEST", "PROG");
        assertFalse(has(result.findings(), "PRIV_PERSONAL_DATA_IN_SPOOL"));
        assertTrue(has(result.suppressedFindings(), "PRIV_PERSONAL_DATA_IN_SPOOL"));
    }

    @Test
    void suppressionWithoutReasonDoesNotSuppress() {
        String code = """
                WRITE ls_p-pernr. "#EC ABAP_GUARDIAN: PRIV_PERSONAL_DATA_IN_SPOOL
                """;
        AnalysisResult result = engine().analyze(code, "ZTEST", "PROG");
        assertTrue(has(result.findings(), "PRIV_PERSONAL_DATA_IN_SPOOL"),
                "a suppression without a reason must be ignored");
    }

    @Test
    void severityOverrideFromYamlConfiguration() {
        String yaml = """
                rules:
                  PERF_SELECT_STAR:
                    severity: CRITICAL
                """;
        RuleConfiguration config = RuleConfiguration.fromYaml(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        RuleEngine engine = new RuleEngine(RuleRegistry.allRules(), config);
        List<Finding> result = engine.analyze(
                "SELECT * FROM mara INTO TABLE lt_mara WHERE matnr = lv_matnr.", "ZTEST", "PROG").findings();
        Finding f = result.stream().filter(x -> x.getRuleId().equals("PERF_SELECT_STAR"))
                .findFirst().orElseThrow();
        assertEquals(RuleSeverity.CRITICAL, f.getSeverity());
    }

    @Test
    void disabledRuleProducesNoFindings() {
        String yaml = """
                rules:
                  PERF_SELECT_STAR:
                    enabled: false
                """;
        RuleConfiguration config = RuleConfiguration.fromYaml(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        RuleEngine engine = new RuleEngine(RuleRegistry.allRules(), config);
        List<Finding> result = engine.analyze(
                "SELECT * FROM mara INTO TABLE lt_mara WHERE matnr = lv_matnr.", "ZTEST", "PROG").findings();
        assertFalse(result.stream().anyMatch(f -> f.getRuleId().equals("PERF_SELECT_STAR")));
    }

    @Test
    void confidenceThresholdFiltersFindings() {
        String yaml = """
                rules:
                  PERF_SELECT_STAR:
                    confidenceThreshold: 0.99
                """;
        RuleConfiguration config = RuleConfiguration.fromYaml(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        RuleEngine engine = new RuleEngine(RuleRegistry.allRules(), config);
        List<Finding> result = engine.analyze(
                "SELECT * FROM mara INTO TABLE lt_mara WHERE matnr = lv_matnr.", "ZTEST", "PROG").findings();
        assertFalse(result.stream().anyMatch(f -> f.getRuleId().equals("PERF_SELECT_STAR")));
    }
}
