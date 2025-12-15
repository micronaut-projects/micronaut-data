package example.schema;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Loads JSON Schemas from classpath resources.
 * Default location: META-INF/schemas/{subject}.schema.json
 */
@Singleton
public class JsonSchemaService {

    private static final String DEFAULT_LOCATION = "META-INF/schemas/";

    /**
     * Load the schema body for a given subject from the default classpath location.
     * @param subject Subject (typically class simple name, e.g. MoonPhase)
     * @return Optional schema body
     */
    public Optional<String> loadSchemaBody(String subject) {
        return loadSchemaBody(DEFAULT_LOCATION, subject);
    }

    /**
     * Load the schema body from a specific base directory on the classpath.
     * @param baseDir e.g. META-INF/schemas/
     * @param subject subject (filename without extension)
     * @return Optional schema body
     */
    public Optional<String> loadSchemaBody(String baseDir, String subject) {
        String path = baseDir + subject + ".schema.json";
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (in == null) {
            return Optional.empty();
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String content = br.lines().collect(Collectors.joining("\n"));
            return Optional.of(content);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Convenience to resolve subject name from a class simple name.
     */
    public String subjectForClass(Class<?> type) {
        return type.getSimpleName();
    }

    /**
     * If the resource exists.
     */
    public boolean exists(String subject) {
        String path = DEFAULT_LOCATION + subject + ".schema.json";
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (in != null) {
            try {
                in.close();
            } catch (Exception ignored) { }
            return true;
        }
        return false;
    }

    /**
     * Optionally returns the $id field if embedded in the schema (best-effort).
     */
    public @Nullable String extractId(String schemaBody) {
        // very small heuristic to avoid introducing a JSON parser
        int idx = schemaBody.indexOf("\"$id\"");
        if (idx < 0) return null;
        int colon = schemaBody.indexOf(':', idx);
        if (colon < 0) return null;
        int firstQuote = schemaBody.indexOf('"', colon);
        if (firstQuote < 0) return null;
        int secondQuote = schemaBody.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) return null;
        return schemaBody.substring(firstQuote + 1, secondQuote);
    }
}
