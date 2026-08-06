package com.abapguardian.eclipse.service;

import com.abapguardian.eclipse.api.GuardianAnalysisResult;
import com.abapguardian.eclipse.api.GuardianFinding;
import com.abapguardian.eclipse.preferences.GuardianPreferences;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client for the ABAP Guardian gateway. Uses the JDK HttpClient only
 * (no third-party dependencies inside the OSGi bundle) and a deliberately
 * small hand-rolled JSON layer ({@link JsonLite}) for the fixed wire format.
 */
public class GatewayClient {

    private final HttpClient http;
    private final String baseUrl;
    private final Duration timeout;

    public GatewayClient() {
        this(GuardianPreferences.getServiceUrl(), GuardianPreferences.getTimeoutSeconds());
    }

    public GatewayClient(String baseUrl, int timeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** True when GET /health answers with HTTP 200. */
    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    public GuardianAnalysisResult analyze(String source, String objectName, String objectType,
                                          boolean useAi) throws GatewayException {
        JsonLite.Obj payload = new JsonLite.Obj()
                .put("source", source)
                .put("objectName", objectName)
                .put("objectType", objectType)
                .put("useAi", useAi);
        JsonLite.Obj body = post("/api/v1/analyze", payload);
        return new GuardianAnalysisResult(
                body.str("objectName", objectName),
                body.str("objectType", objectType),
                parseFindings(body.arr("findings")),
                parseFindings(body.arr("suppressedFindings")),
                body.bool("aiEnhanced", false));
    }

    private JsonLite.Obj post(String path, JsonLite.Obj payload) throws GatewayException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toJson(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new GatewayException("Gateway returned HTTP " + response.statusCode());
            }
            return JsonLite.parseObject(response.body());
        } catch (IOException e) {
            throw new GatewayException("Cannot reach ABAP Guardian gateway at " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayException("Analysis was interrupted", e);
        }
    }

    private List<GuardianFinding> parseFindings(List<JsonLite.Obj> array) {
        List<GuardianFinding> findings = new ArrayList<>();
        for (JsonLite.Obj f : array) {
            findings.add(new GuardianFinding(
                    f.str("ruleId", ""),
                    f.str("category", ""),
                    f.str("severity", "INFO"),
                    f.num("confidence", 0.0),
                    f.str("title", ""),
                    f.str("explanation", ""),
                    f.str("evidence", ""),
                    (int) f.num("startLine", 1),
                    (int) f.num("startColumn", 1),
                    (int) f.num("endLine", 1),
                    (int) f.num("endColumn", 1),
                    f.str("recommendation", ""),
                    f.strOrNull("suggestedCode"),
                    f.bool("requiresHumanReview", false),
                    f.strList("documentationReferences")));
        }
        return findings;
    }

    public static class GatewayException extends Exception {
        private static final long serialVersionUID = 1L;

        public GatewayException(String message) {
            super(message);
        }

        public GatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
