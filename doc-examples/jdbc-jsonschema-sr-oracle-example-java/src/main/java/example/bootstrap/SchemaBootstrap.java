package example.bootstrap;

import example.schema.JsonSchemaService;
import example.sr.SchemaRegistryClient;
import io.micronaut.context.annotation.Value;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * Optional startup bootstrap that can:
 *  - ensure subject-level compatibility (BACKWARD) in CSR
 *  - register the JSON Schema for a subject if present on the classpath
 *
 * Disabled by default; enable via:
 *   bootstrap.register=true
 */
@Singleton
public class SchemaBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaBootstrap.class);

    private final boolean register;
    private final String subject;
    private final SchemaRegistryClient sr;
    private final JsonSchemaService schemaService;

    public SchemaBootstrap(
            @Value("${bootstrap.register:false}") boolean register,
            // Subject based on class simple name; change here if a different subject is desired
            @Value("${bootstrap.subject:MoonPhase}") String subject,
            SchemaRegistryClient sr,
            JsonSchemaService schemaService
    ) {
        this.register = register;
        this.subject = subject;
        this.sr = sr;
        this.schemaService = schemaService;
    }

    @EventListener
    void onStartup(ServerStartupEvent event) {
        if (!register) {
            LOG.debug("SchemaBootstrap: registration disabled (bootstrap.register=false)");
            return;
        }
        if (!sr.isConfigured()) {
            LOG.warn("SchemaBootstrap: CSR not configured (csr.url missing); skipping registration");
            return;
        }
        Optional<String> schemaOpt = schemaService.loadSchemaBody(subject);
        if (schemaOpt.isEmpty()) {
            LOG.warn("SchemaBootstrap: schema for subject '{}' not found on classpath", subject);
            return;
        }
        String schemaBody = schemaOpt.get();

        try {
            LOG.info("SchemaBootstrap: ensuring subject compatibility '{}' -> {}", subject, "BACKWARD");
            sr.ensureSubjectCompatibility(subject);
        } catch (Exception e) {
            LOG.warn("SchemaBootstrap: could not set compatibility for subject {}", subject, e);
        }

        try {
            var idOpt = sr.registerSchema(subject, schemaBody);
            if (idOpt.isPresent()) {
                LOG.info("SchemaBootstrap: registered schema for subject '{}' with id {}", subject, idOpt.get());
            } else {
                LOG.warn("SchemaBootstrap: registration for subject '{}' returned empty result", subject);
            }
        } catch (Exception e) {
            LOG.error("SchemaBootstrap: error registering schema for subject {}", subject, e);
        }
    }
}
