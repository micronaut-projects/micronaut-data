package example

import io.micronaut.data.cosmos.config.StorageUpdatePolicy
import io.micronaut.test.support.TestPropertyProvider
import org.junit.Rule
import org.junit.jupiter.api.AfterAll
import org.testcontainers.containers.CosmosDBEmulatorContainer
import org.testcontainers.utility.DockerImageName
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path

abstract class AbstractAzureCosmosTest : TestPropertyProvider {

    @Rule
    var emulator = CosmosDBEmulatorContainer(DockerImageName.parse("mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator:latest"))

    @AfterAll
    open fun tearDown() {
        if (emulator.isRunning) {
            emulator.stop()
        }
    }

    override fun getProperties(): Map<String, String> {
        emulator.start()
        val keyStoreFile: Path
        try {
            keyStoreFile = Files.createTempFile("azure-cosmos-emulator", ".keystore")
            val keyStore = emulator.buildNewKeyStore()
            keyStore.store(FileOutputStream(keyStoreFile.toFile()), emulator.emulatorKey.toCharArray())
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize Azure Cosmos Emulator", e)
        }

        System.setProperty("javax.net.ssl.trustStore", keyStoreFile.toString())
        System.setProperty("javax.net.ssl.trustStorePassword", emulator.emulatorKey)
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12")

        return mapOf(
            "azure.cosmos.default-gateway-mode" to "true",
            "azure.cosmos.endpoint-discovery-enabled" to "false",
            "azure.cosmos.endpoint" to emulator.emulatorEndpoint,
            "azure.cosmos.key" to emulator.emulatorKey,
            "azure.cosmos.database.throughput-settings.request-units" to "1000",
            "azure.cosmos.database.throughput-settings.auto-scale" to "true",
            "azure.cosmos.database.database-name" to "testDb",
            "azure.cosmos.database.update-policy" to StorageUpdatePolicy.CREATE_IF_NOT_EXISTS.name
        )
    }

}
