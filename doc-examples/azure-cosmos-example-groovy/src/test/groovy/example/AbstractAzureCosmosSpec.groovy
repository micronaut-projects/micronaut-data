package example

import io.micronaut.data.cosmos.config.StorageUpdatePolicy
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.CosmosDBEmulatorContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore

abstract class AbstractAzureCosmosSpec extends Specification implements TestPropertyProvider {

    @Shared
    @AutoCleanup("stop")
    CosmosDBEmulatorContainer emulator = new CosmosDBEmulatorContainer(DockerImageName.parse("mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator:latest"))

    @Override
    Map<String, String> getProperties() {
        emulator.start()
        Path keyStoreFile
        try {
            keyStoreFile = Files.createTempFile("azure-cosmos-emulator", ".keystore")
            KeyStore keyStore = emulator.buildNewKeyStore()
            keyStore.store(new FileOutputStream(keyStoreFile.toFile()), emulator.getEmulatorKey().toCharArray())
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Azure Cosmos Emulator", e)
        }

        System.setProperty("javax.net.ssl.trustStore", keyStoreFile.toString())
        System.setProperty("javax.net.ssl.trustStorePassword", emulator.getEmulatorKey())
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12")

        Map<String, String> defaultProps = new HashMap<>()
        defaultProps.put("azure.cosmos.default-gateway-mode", "true")
        defaultProps.put("azure.cosmos.endpoint-discovery-enabled", "false")
        defaultProps.put("azure.cosmos.endpoint", emulator.getEmulatorEndpoint())
        defaultProps.put("azure.cosmos.key", emulator.getEmulatorKey())
        defaultProps.put("azure.cosmos.database.throughput-settings.request-units", "1000")
        defaultProps.put("azure.cosmos.database.throughput-settings.auto-scale", "true")
        defaultProps.put("azure.cosmos.database.database-name", "testDb")
        defaultProps.put("azure.cosmos.database.update-policy", StorageUpdatePolicy.CREATE_IF_NOT_EXISTS.name())
        return defaultProps
    }
}
