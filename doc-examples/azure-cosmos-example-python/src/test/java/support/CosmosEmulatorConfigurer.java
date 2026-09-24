package support;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.SystemPropertiesPropertySource;
import io.micronaut.data.cosmos.config.StorageUpdatePolicy;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.CosmosDBEmulatorContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Starts the Azure Cosmos emulator (Testcontainers) and supplies its connection properties to the Python
 * tests, like {@code AbstractAzureCosmosTest} ({@code TestPropertyProvider}) does for the Java tests.
 * <p>
 * The configurer is written in Java because Micronaut Test calls {@code TestPropertyProvider} before the
 * application context, and with it the GraalPy runtime, exists, so a Python test class cannot supply the
 * container properties.
 */
@ContextConfigurer
public class CosmosEmulatorConfigurer implements ApplicationContextConfigurer {

    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);

    private static final CosmosDBEmulatorContainer EMULATOR = new CosmosDBEmulatorContainer(DockerImageName.parse("mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator:vnext-preview")
        .asCompatibleSubstituteFor("mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator"))
        .waitingFor(Wait.forLogMessage(".*PostgreSQL=OK, Gateway=OK, Explorer=OK.*", 1).withStartupTimeout(STARTUP_TIMEOUT))
        .withCommand("--protocol", "https");

    @Override
    public void configure(ApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        if (environment.getActiveNames().contains(Environment.TEST) && DockerClientFactory.instance().isDockerAvailable()) {
            environment.addPropertySource(PropertySource.of("cosmos-emulator", getProperties(), SystemPropertiesPropertySource.POSITION + 100));
        }
    }

    private static Map<String, Object> getProperties() {
        EMULATOR.start();
        Path keyStoreFile;
        try {
            keyStoreFile = Files.createTempFile("azure-cosmos-emulator", ".keystore");
            KeyStore keyStore = EMULATOR.buildNewKeyStore();
            keyStore.store(new FileOutputStream(keyStoreFile.toFile()), EMULATOR.getEmulatorKey().toCharArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Azure Cosmos Emulator", e);
        }

        System.setProperty("javax.net.ssl.trustStore", keyStoreFile.toString());
        System.setProperty("javax.net.ssl.trustStorePassword", EMULATOR.getEmulatorKey());
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");

        Map<String, Object> defaultProps = new HashMap<>();
        defaultProps.put("azure.cosmos.default-gateway-mode", "true");
        defaultProps.put("azure.cosmos.endpoint-discovery-enabled", "false");
        defaultProps.put("azure.cosmos.endpoint", EMULATOR.getEmulatorEndpoint());
        defaultProps.put("azure.cosmos.key", EMULATOR.getEmulatorKey());
        defaultProps.put("azure.cosmos.database.throughput-settings.request-units", "1000");
        defaultProps.put("azure.cosmos.database.throughput-settings.auto-scale", "true");
        defaultProps.put("azure.cosmos.database.database-name", "testDb");
        defaultProps.put("azure.cosmos.database.update-policy", StorageUpdatePolicy.CREATE_IF_NOT_EXISTS.name());
        return defaultProps;
    }
}
