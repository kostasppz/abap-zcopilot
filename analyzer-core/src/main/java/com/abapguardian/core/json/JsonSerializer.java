package com.abapguardian.core.json;

import com.abapguardian.core.engine.AnalysisResult;
import com.abapguardian.core.rule.Finding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * JSON serialization for analysis results. A hand-rolled mapping (rather than
 * reflective bean mapping) keeps the wire format explicit and stable.
 */
public final class JsonSerializer {

    private final ObjectMapper mapper = new ObjectMapper();

    public String toJson(AnalysisResult result) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toNode(result));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize analysis result", e);
        }
    }

    public JsonNode toNode(AnalysisResult result) {
        ObjectNode root = mapper.createObjectNode();
        root.put("objectName", result.objectName());
        root.put("objectType", result.objectType());
        ArrayNode findings = root.putArray("findings");
        for (Finding f : result.findings()) {
            findings.add(findingNode(f));
        }
        ArrayNode suppressed = root.putArray("suppressedFindings");
        for (Finding f : result.suppressedFindings()) {
            suppressed.add(findingNode(f));
        }
        return root;
    }

    public ObjectNode findingNode(Finding f) {
        ObjectNode node = mapper.createObjectNode();
        node.put("ruleId", f.getRuleId());
        node.put("category", f.getCategory().name());
        node.put("severity", f.getSeverity().name());
        node.put("confidence", f.getConfidence());
        node.put("title", f.getTitle());
        node.put("explanation", f.getExplanation());
        node.put("evidence", f.getEvidence());
        node.put("startLine", f.getStartLine());
        node.put("startColumn", f.getStartColumn());
        node.put("endLine", f.getEndLine());
        node.put("endColumn", f.getEndColumn());
        node.put("recommendation", f.getRecommendation());
        if (f.getSuggestedCode() != null) {
            node.put("suggestedCode", f.getSuggestedCode());
        } else {
            node.putNull("suggestedCode");
        }
        node.put("requiresHumanReview", f.isRequiresHumanReview());
        ArrayNode refs = node.putArray("documentationReferences");
        for (String ref : f.getDocumentationReferences()) {
            refs.add(ref);
        }
        return node;
    }
}
