package example.sr;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal Confluent Schema Registry client for JSON Schemas.
 * Supports:
 *  - Fetch latest schema for a subject (raw schema string)
 *  - Register a schema version for a subject
 *
 * This example keeps the implementation deliberately simple and synchronous.
 */
@Singleton
public class SchemaRegistryClient {

    private final @Nullable String baseUrl;
    private final String authType;
    private final @Nullable String bearerToken;
    private final @Nullable String basicUser;
    private final @Nullable String basicPassword;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final String compatibility; // e.g. BACKWARD

    public SchemaRegistryClient(
            @io.micronaut.context.annotation.Value("${csr.url:}") @Nullable String baseUrl,
            @io.micronaut.context.annotation.Value("${csr.auth.type:none}") String authType,
            @io.micronaut.context.annotation.Value("${csr.auth.token:}") @Nullable String bearerToken,
            @io.micronaut.context.annotation.Value("${csr.auth.username:}") @Nullable String basicUser,
            @io.micronaut.context.annotation.Value("${csr.auth.password:}") @Nullable String basicPassword,
            @io.micronaut.context.annotation.Value("${csr.timeouts.connect-ms:2000}") int connectTimeoutMs,
            @io.micronaut.context.annotation.Value("${csr.timeouts.read-ms:5000}") int readTimeoutMs,
            @io.micronaut.context.annotation.Value("${csr.compatibility:BACKWARD}") String compatibility
    ) {
        this.baseUrl = isBlank(baseUrl) ? null : baseUrl;
        this.authType = authType;
        this.bearerToken = bearerToken;
        this.basicUser = basicUser;
        this.basicPassword = basicPassword;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.compatibility = compatibility;
    }

    public boolean isConfigured() {
        return baseUrl != null;
    }

    /**
     * Returns the latest schema body for the subject from CSR, if reachable and configured.
     */
    public Optional<String> fetchLatestSchema(String subject) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        String safeSubject = urlEncode(subject);
        String url = baseUrl + "/subjects/" + safeSubject + "/versions/latest/schema";
        try (HttpClient client = buildClient(baseUrl)) {
            MutableHttpRequest<?> req = HttpRequest.GET(url)
                    .accept(MediaType.APPLICATION_JSON_TYPE);
            req = withAuth(req);
            String body = client.toBlocking().retrieve(req);
            return Optional.ofNullable(body);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Registers the given JSON Schema for the subject. If the same schema already exists,
     * CSR will deduplicate and return an existing id.
     */
    public Optional<Integer> registerSchema(String subject, String schemaBody) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        String safeSubject = urlEncode(subject);
        String url = baseUrl + "/subjects/" + safeSubject + "/versions";
        try (HttpClient client = buildClient(baseUrl)) {
            String escaped = schemaBody
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
            String payload = "{\"schemaType\":\"JSON\",\"schema\":\"" + escaped + "\"}";
            MutableHttpRequest<String> req = HttpRequest.POST(url, payload)
                    .contentType(MediaType.APPLICATION_JSON_TYPE)
                    .accept(MediaType.APPLICATION_JSON_TYPE);
            req = withAuth(req);

            String response = client.toBlocking().retrieve(req);
            Integer id = extractId(response);
            if (id != null) {
                return Optional.of(id);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    /**
     * Optionally set subject-level compatibility at CSR (BACKWARD/..).
     */
    public void ensureSubjectCompatibility(String subject) {
        if (!isConfigured()) {
            return;
        }
        String safeSubject = urlEncode(subject);
        String url = baseUrl + "/config/" + safeSubject;
        String payload = "{\"compatibility\":\"" + compatibility + "\"}";
        try (HttpClient client = buildClient(baseUrl)) {
            MutableHttpRequest<String> req = HttpRequest.PUT(url, payload)
                    .contentType(MediaType.APPLICATION_JSON_TYPE)
                    .accept(MediaType.APPLICATION_JSON_TYPE);
            req = withAuth(req);
            client.toBlocking().exchange(req);
        } catch (Exception ignored) {
        }
    }

    private static @Nullable Integer extractId(String json) {
        try {
            int idx = json.indexOf("\"id\"");
            if (idx < 0) return null;
            int colon = json.indexOf(':', idx);
            if (colon < 0) return null;
            int start = colon + 1;
            while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
            int end = start;
            while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
            if (start == end) return null;
            return Integer.parseInt(json.substring(start, end));
        } catch (Exception e) {
            return null;
        }
    }

    // -- helpers

    private HttpClient buildClient(String base) throws IOException {
        return HttpClient.create(new URL(base));
    }

    private <T> MutableHttpRequest<T> withAuth(MutableHttpRequest<T> req) {
        if ("bearer".equalsIgnoreCase(authType) && !isBlank(bearerToken)) {
            return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        if ("basic".equalsIgnoreCase(authType) && !isBlank(basicUser) && !isBlank(basicPassword)) {
            String basic = basicUser + ":" + basicPassword;
            String encoded = Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8));
            return req.header(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }
        return req;
    }

    private static boolean isBlank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
