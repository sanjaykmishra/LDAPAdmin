package com.ldapadmin.service.hr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Read-only HTTP client for the BambooHR REST API.
 * Auth: HTTP Basic with the API key as password and a literal "x" as username.
 */
@Component
@Slf4j
public class BambooHrClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String API_BASE = "https://%s.bamboohr.com/api/gateway.php/%s/v1";

    private final ObjectMapper objectMapper;

    /** Shared HTTP client — reused across requests to avoid per-request overhead. */
    private volatile HttpClient sharedHttpClient;

    public BambooHrClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches all employees from the BambooHR directory endpoint.
     */
    public List<Map<String, String>> fetchAllEmployees(String subdomain, String apiKey) {
        String url = String.format(API_BASE + "/employees/directory", subdomain, subdomain);
        String json = doGet(url, apiKey);
        return parseEmployeeDirectory(json);
    }

    /**
     * Tests API connectivity with a lightweight meta/fields call.
     * Returns the employee count on success via a separate directory fetch, or -1 on failure.
     */
    public int testConnection(String subdomain, String apiKey) {
        try {
            // Use a lightweight endpoint to test auth without fetching all employees
            String url = String.format(API_BASE + "/meta/fields/", subdomain, subdomain);
            doGet(url, apiKey);
            // If meta/fields succeeds, auth is valid — fetch employee count
            List<Map<String, String>> employees = fetchAllEmployees(subdomain, apiKey);
            return employees.size();
        } catch (Exception e) {
            log.warn("BambooHR connection test failed for subdomain '{}': {}", subdomain, e.getMessage());
            return -1;
        }
    }

    private String doGet(String url, String apiKey) {
        String credentials = apiKey + ":x";
        String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Authorization", basicAuth)
                    .GET()
                    .build();

            HttpResponse<String> response = getHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                throw new RuntimeException("BambooHR authentication failed — check API key");
            }
            if (response.statusCode() == 429) {
                throw new RuntimeException("BambooHR rate limit exceeded — try again later");
            }
            if (response.statusCode() >= 400) {
                throw new RuntimeException("BambooHR API returned HTTP " + response.statusCode()
                        + ": " + response.body());
            }

            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call BambooHR API: " + e.getMessage(), e);
        }
    }

    private HttpClient getHttpClient() {
        if (sharedHttpClient == null) {
            synchronized (this) {
                if (sharedHttpClient == null) {
                    sharedHttpClient = HttpClient.newBuilder()
                            .connectTimeout(CONNECT_TIMEOUT)
                            .build();
                }
            }
        }
        return sharedHttpClient;
    }

    public List<Map<String, String>> parseEmployeeDirectory(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode employees = root.has("employees") ? root.get("employees") : root;
            if (!employees.isArray()) {
                return List.of();
            }
            List<Map<String, String>> result = new ArrayList<>();
            for (JsonNode node : employees) {
                Map<String, String> fields = objectMapper.convertValue(node,
                        new TypeReference<Map<String, String>>() {});
                result.add(fields);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse BambooHR employee directory response", e);
        }
    }
}
