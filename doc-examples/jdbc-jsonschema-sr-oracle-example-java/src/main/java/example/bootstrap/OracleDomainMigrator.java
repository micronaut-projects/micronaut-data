package example.bootstrap;

import example.util.Names;
import io.micronaut.context.annotation.Value;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs the Oracle JSON DOMAIN DDL needed to validate using the canonical schema URL.
 *
 * Execution of DDL is intentionally NOT performed to avoid bringing an Oracle JDBC dependency
 * into the example. Operators can copy the logged SQL into their migration tool (Flyway/Liquibase)
 * or wire their own JDBC execution if desired.
 *
 * Enable via:
 *   bootstrap.migrate-oracle-domain=true
 * Customize via:
 *   bootstrap.subject=MoonPhase
 *   schema-proxy.base-url=http://localhost:8083
 */
@Singleton
public class OracleDomainMigrator {

    private static final Logger LOG = LoggerFactory.getLogger(OracleDomainMigrator.class);

    private final boolean migrate;
    private final String subject;
    private final String proxyBaseUrl;

    public OracleDomainMigrator(
            @Value("${bootstrap.migrate-oracle-domain:false}") boolean migrate,
            @Value("${bootstrap.subject:MoonPhase}") String subject,
            @Value("${schema-proxy.base-url:http://localhost:8083}") String proxyBaseUrl
    ) {
        this.migrate = migrate;
        this.subject = subject;
        this.proxyBaseUrl = proxyBaseUrl;
    }

    @EventListener
    void onStartup(ServerStartupEvent event) {
        if (!migrate) {
            LOG.debug("OracleDomainMigrator: disabled (bootstrap.migrate-oracle-domain=false)");
            return;
        }
        String domainName = Names.toUpperSnake(subject);
        String canonicalUrl = proxyBaseUrl.replaceAll("/+$", "") + "/schemas/" + subject + "/current";
        String ddl = "CREATE DOMAIN " + domainName + " AS JSON\n"
                + "  VALIDATE USING '" + canonicalUrl + "';";

        LOG.info("OracleDomainMigrator:\n{}", ddl);
        LOG.info("Note: This example does not execute DDL. Apply it using your migration tool.");
    }
}
