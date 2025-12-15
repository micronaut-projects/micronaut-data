package example.proxy;

import example.schema.JsonSchemaService;
import example.sr.SchemaRegistryClient;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Exposes canonical schema URLs for Oracle to reach.
 * Default endpoint: /schemas/{subject}/current
 *
 * Strategy:
 * - If CSR is configured and reachable, return its latest schema for the subject.
 * - Otherwise fall back to the classpath schema at META-INF/schemas/{subject}.schema.json
 */
@Controller("/schemas")
@Singleton
public class SchemaProxyController {

    private final SchemaRegistryClient sr;
    private final JsonSchemaService schemaService;

    public SchemaProxyController(SchemaRegistryClient sr, JsonSchemaService schemaService) {
        this.sr = sr;
        this.schemaService = schemaService;
    }

    @Get(uri = "/{subject}/current", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<String> current(@PathVariable String subject) {
        // 1) Try CSR latest
        Optional<String> body = sr.fetchLatestSchema(subject);
        // 2) Fallback to classpath
        if (body.isEmpty()) {
            body = schemaService.loadSchemaBody(subject);
        }
        if (body.isEmpty()) {
            return HttpResponse.notFound();
        }
        String json = body.get();
        String etag = computeEtag(json);
        return HttpResponse.ok(json)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.ETAG, etag)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=300, must-revalidate");
    }

    private static String computeEtag(String body) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(body.getBytes(StandardCharsets.UTF_8));
            return "\"" + HexFormat.of().formatHex(digest) + "\"";
        } catch (Exception e) {
            return "\"0\"";
        }
    }
}
