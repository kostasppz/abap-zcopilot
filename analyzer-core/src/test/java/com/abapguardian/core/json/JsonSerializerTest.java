package com.abapguardian.core.json;

import com.abapguardian.core.config.RuleConfiguration;
import com.abapguardian.core.engine.AnalysisResult;
import com.abapguardian.core.engine.RuleEngine;
import com.abapguardian.core.rules.RuleRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSerializerTest {

    @Test
    void serializesAllFindingFields() throws Exception {
        RuleEngine engine = new RuleEngine(RuleRegistry.allRules(), RuleConfiguration.defaults());
        AnalysisResult result = engine.analyze(
                "SELECT * FROM pa0002 INTO TABLE lt_p WHERE pernr = lv.", "ZCL_EXAMPLE", "CLAS");
        String json = new JsonSerializer().toJson(result);
        JsonNode root = new ObjectMapper().readTree(json);
        assertEquals("ZCL_EXAMPLE", root.get("objectName").asText());
        assertEquals("CLAS", root.get("objectType").asText());
        assertTrue(root.get("findings").size() > 0);
        JsonNode finding = root.get("findings").get(0);
        for (String field : new String[]{"ruleId", "category", "severity", "confidence", "title",
                "explanation", "evidence", "startLine", "startColumn", "endLine", "endColumn",
                "recommendation", "suggestedCode", "requiresHumanReview", "documentationReferences"}) {
            assertTrue(finding.has(field), "missing field: " + field);
        }
    }
}
