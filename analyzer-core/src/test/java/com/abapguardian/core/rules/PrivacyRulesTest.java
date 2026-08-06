package com.abapguardian.core.rules;

import com.abapguardian.core.config.RuleConfiguration;
import com.abapguardian.core.engine.RuleEngine;
import com.abapguardian.core.rule.Finding;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyRulesTest {

    private List<Finding> findings(String code) {
        return new RuleEngine(RuleRegistry.allRules(), RuleConfiguration.defaults())
                .analyze(code, "ZTEST", "PROG").findings();
    }

    private boolean has(List<Finding> findings, String ruleId) {
        return findings.stream().anyMatch(f -> f.getRuleId().equals(ruleId));
    }

    @Test
    void personalDataInSpool() {
        assertTrue(has(findings("WRITE: / ls_p-pernr, ls_p-nachn."), "PRIV_PERSONAL_DATA_IN_SPOOL"));
    }

    @Test
    void sensitiveIdentifierAloneIsNotAViolation() {
        // Just declaring or moving sensitive fields is not an output context.
        String code = """
                DATA lv_pernr TYPE pernr_d.
                lv_pernr = ls_p0002-pernr.
                """;
        List<Finding> f = findings(code);
        assertFalse(has(f, "PRIV_PERSONAL_DATA_IN_SPOOL"));
        assertFalse(has(f, "PRIV_PERSONAL_DATA_IN_MESSAGE"));
        assertFalse(has(f, "PRIV_PERSONAL_DATA_IN_LOG"));
    }

    @Test
    void personalDataInMessage() {
        assertTrue(has(findings("MESSAGE i001(zz) WITH ls_p-nachn ls_p-vorna."),
                "PRIV_PERSONAL_DATA_IN_MESSAGE"));
    }

    @Test
    void broadHrSelection() {
        assertTrue(has(findings("SELECT * FROM pa0002 INTO TABLE lt_p WHERE pernr = lv_pernr."),
                "PRIV_BROAD_HR_MASTER_DATA_SELECTION"));
    }

    @Test
    void narrowHrSelectionIsAccepted() {
        assertFalse(has(findings("SELECT pernr FROM pa0002 INTO TABLE lt_p WHERE pernr = lv_pernr."),
                "PRIV_BROAD_HR_MASTER_DATA_SELECTION"));
    }

    @Test
    void unmaskedPernrInOutput() {
        assertTrue(has(findings("WRITE lv_pernr."), "PRIV_UNMASKED_PERSONNEL_NUMBER"));
    }

    @Test
    void fileExportOfSensitiveData() {
        assertTrue(has(findings("TRANSFER ls_p-nachn TO lv_file."),
                "PRIV_PERSONAL_DATA_IN_FILE_EXPORT"));
    }

    @Test
    void excessiveFieldSelection() {
        String code = "SELECT pernr nachn vorna gbdat stras FROM pa0002 INTO TABLE lt WHERE pernr = lv.";
        assertTrue(has(findings(code), "PRIV_EXCESSIVE_FIELD_SELECTION"));
    }

    @Test
    void externalTransferToUnapprovedDestination() {
        String code = """
                SELECT pernr nachn FROM pa0002 INTO TABLE lt_p WHERE pernr = lv_pernr.
                CALL FUNCTION 'Z_SEND_DATA' DESTINATION 'EXTERNAL_SYS' EXPORTING it_data = lt_p.
                """;
        assertTrue(has(findings(code), "PRIV_EXTERNAL_DATA_TRANSFER"));
    }

    @Test
    void approvedDestinationIsAccepted() {
        String yaml = """
                approvedDestinations:
                  - EXTERNAL_SYS
                """;
        RuleConfiguration config = RuleConfiguration.fromYaml(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        RuleEngine engine = new RuleEngine(RuleRegistry.allRules(), config);
        String code = """
                SELECT pernr nachn FROM pa0002 INTO TABLE lt_p WHERE pernr = lv_pernr.
                CALL FUNCTION 'Z_SEND_DATA' DESTINATION 'EXTERNAL_SYS' EXPORTING it_data = lt_p.
                """;
        assertFalse(engine.analyze(code, "ZTEST", "PROG").findings().stream()
                .anyMatch(f -> f.getRuleId().equals("PRIV_EXTERNAL_DATA_TRANSFER")));
    }

    @Test
    void configurableSensitiveFields() {
        String yaml = """
                sensitiveFields:
                  - ZZCUSTOM_FIELD
                """;
        RuleConfiguration config = RuleConfiguration.fromYaml(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        RuleEngine engine = new RuleEngine(RuleRegistry.allRules(), config);
        // Default field NACHN no longer sensitive; custom field is.
        assertFalse(engine.analyze("WRITE ls_p-nachn.", "Z", "PROG").findings().stream()
                .anyMatch(f -> f.getRuleId().equals("PRIV_PERSONAL_DATA_IN_SPOOL")));
        assertTrue(engine.analyze("WRITE ls_p-zzcustom_field.", "Z", "PROG").findings().stream()
                .anyMatch(f -> f.getRuleId().equals("PRIV_PERSONAL_DATA_IN_SPOOL")));
    }

    @Test
    void allPrivacyFindingsRequireHumanReview() {
        String code = """
                SELECT * FROM pa0002 INTO TABLE lt_p.
                WRITE: / ls_p-pernr, ls_p-nachn.
                MESSAGE i001(zz) WITH ls_p-vorna.
                """;
        for (Finding f : findings(code)) {
            if (f.getRuleId().startsWith("PRIV_")) {
                assertTrue(f.isRequiresHumanReview(), f.getRuleId() + " must require human review");
                assertTrue(f.getConfidence() < 1.0, f.getRuleId() + " must not claim certainty");
            }
        }
    }
}
