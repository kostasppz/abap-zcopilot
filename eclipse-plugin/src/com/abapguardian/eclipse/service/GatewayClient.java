package com.abapguardian.eclipse.service;

import com.abapguardian.eclipse.api.GuardianAnalysisResult;
import com.abapguardian.eclipse.api.GuardianChatMessage;
import com.abapguardian.eclipse.api.GuardianChatResponse;
import com.abapguardian.eclipse.api.GuardianFinding;
import com.abapguardian.eclipse.preferences.GuardianPreferences;
import com.abapguardian.eclipse.security.SecureCredentialStore;

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
    private final String apiToken;

    public GatewayClient() {
        this(GuardianPreferences.getServiceUrl(), GuardianPreferences.getTimeoutSeconds(),
                new SecureCredentialStore().getGuardianApiToken().orElse(""));
    }

    public GatewayClient(String baseUrl, int timeoutSeconds) {
        this(baseUrl, timeoutSeconds, "");
    }

    public GatewayClient(String baseUrl, int timeoutSeconds, String apiToken) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.baseUrl = normalizedBaseUrl.endsWith("/")
                ? normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1)
                : normalizedBaseUrl;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.apiToken = apiToken == null ? "" : apiToken.trim();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** True when health and an authenticated API probe both answer with HTTP 200. */
    public boolean isHealthy() {
        return testConnection().healthy();
    }

    /**
     * Tests both the public health route and an authenticated route and returns
     * a user-facing result that is safe to show in Eclipse Preferences.
     */
    public ConnectionTestResult testConnection() {
        if (baseUrl.isBlank()) {
            return ConnectionTestResult.failed("Enter the RunPod Guardian service URL first.");
        }
        if (apiToken.isBlank()) {
            return ConnectionTestResult.failed("Enter and securely store the Guardian API token first.");
        }
        try {
            int healthStatus = sendProbe("/health");
            if (healthStatus != 200) {
                return ConnectionTestResult.failed(
                        "Guardian /health returned HTTP " + healthStatus + ".");
            }
            int modelsStatus = sendProbe("/api/v1/models");
            if (modelsStatus == 401 || modelsStatus == 403) {
                return ConnectionTestResult.failed(
                        "Guardian is reachable, but the API token was rejected (HTTP "
                                + modelsStatus + ").");
            }
            if (modelsStatus != 200) {
                return ConnectionTestResult.failed(
                        "Guardian /api/v1/models returned HTTP " + modelsStatus + ".");
            }
            return ConnectionTestResult.success(
                    "ABAP Guardian is reachable and the API token is accepted.");
        } catch (IllegalArgumentException e) {
            return ConnectionTestResult.failed("The Guardian service URL is not valid.");
        } catch (IOException e) {
            return ConnectionTestResult.failed(
                    "Cannot reach ABAP Guardian. Check the RunPod Pod and service URL.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ConnectionTestResult.failed("The connection test was cancelled.");
        }
    }

    private int sendProbe(String path) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10)).GET();
        addAuthentication(builder);
        return http.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    public GuardianAnalysisResult analyze(String source, String objectName, String objectType,
                                          boolean useAi) throws GatewayException {
        return analyze(source, objectName, objectType, useAi, List.of());
    }

    public GuardianAnalysisResult analyze(String source, String objectName, String objectType,
                                          boolean useAi, List<String> categories)
            throws GatewayException {
        JsonLite.Obj payload = new JsonLite.Obj()
                .put("source", source)
                .put("objectName", objectName)
                .put("objectType", objectType)
                .put("useAi", useAi)
                .put("categories", categories);
        JsonLite.Obj body = post("/api/v1/analyze", payload);
        return new GuardianAnalysisResult(
                body.str("objectName", objectName),
                body.str("objectType", objectType),
                parseFindings(body.arr("findings")),
                parseFindings(body.arr("suppressedFindings")),
                body.bool("aiEnhanced", false));
    }

    public GuardianChatResponse chat(String question, String source, String selection,
                                     String objectName, String objectType,
                                     List<GuardianChatMessage> history) throws GatewayException {
        List<JsonLite.Obj> turns = new ArrayList<>();
        for (GuardianChatMessage message : history) {
            turns.add(new JsonLite.Obj()
                    .put("role", message.role())
                    .put("content", truncate(message.content(), 4000)));
        }
        JsonLite.Obj payload = new JsonLite.Obj()
                .put("question", question)
                .put("source", source)
                .put("selection", truncate(selection, 4000))
                .put("objectName", objectName)
                .put("objectType", objectType)
                .put("history", turns);
        JsonLite.Obj body = post("/api/v1/chat", payload);
        return new GuardianChatResponse(
                body.str("answer", ""),
                body.str("model", ""),
                body.strList("knowledgeReferences"),
                body.bool("contextIncluded", false));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }

    private JsonLite.Obj post(String path, JsonLite.Obj payload) throws GatewayException {
        if (baseUrl.isBlank()) {
            throw new GatewayException(
                    "Configure the RunPod API URL in ABAP Guardian Preferences first.");
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toJson(), StandardCharsets.UTF_8));
            addAuthentication(builder);
            HttpRequest request = builder.build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                if (response.statusCode() == 401) {
                    throw new GatewayException(
                            "Guardian API token is missing or invalid. Update it in ABAP Guardian Preferences.");
                }
                if (response.statusCode() == 429) {
                    throw new GatewayException("Guardian request limit reached. Please wait and try again.");
                }
                throw new GatewayException("Gateway returned HTTP " + response.statusCode());
            }
            return JsonLite.parseObject(response.body());
        } catch (IOException | IllegalArgumentException e) {
            throw new GatewayException("Cannot reach ABAP Guardian gateway at " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayException("Analysis was interrupted", e);
        }
    }

    private void addAuthentication(HttpRequest.Builder builder) {
        if (!apiToken.isBlank()) {
            builder.header("Authorization", "Bearer " + apiToken);
        }
    }

    /** Result returned by the Preferences connection test. */
    public record ConnectionTestResult(boolean healthy, String message) {

        public static ConnectionTestResult success(String message) {
            return new ConnectionTestResult(true, message);
        }

        public static ConnectionTestResult failed(String message) {
            return new ConnectionTestResult(false, message);
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
