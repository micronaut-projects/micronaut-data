package io.micronaut.data.azure

import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.CosmosDBEmulatorContainer
import org.testcontainers.utility.DockerImageName

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore

trait AzureCosmosTestProperties implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        CosmosDBEmulatorContainer emulator = new CosmosDBEmulatorContainer(
                DockerImageName.parse("mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator:latest")
        )
        emulator.start()
        Path keyStoreFile = Files.createTempFile("azure-cosmos-emulator", ".keystore")
        KeyStore keyStore = emulator.buildNewKeyStore()
        keyStore.store(new FileOutputStream(keyStoreFile.toFile()), emulator.getEmulatorKey().toCharArray())

        System.setProperty("javax.net.ssl.trustStore", keyStoreFile.toString())
        System.setProperty("javax.net.ssl.trustStorePassword", emulator.getEmulatorKey())
        System.setProperty("javax.net.ssl.trustStoreType", "PKCS12")

        return [
                'azure.cosmos.default-gateway-mode'               : 'true',
                'azure.cosmos.endpoint-discovery-enabled'         : 'false',
                'azure.cosmos.endpoint'                           : emulator.getEmulatorEndpoint(),
                'azure.cosmos.key'                                : emulator.getEmulatorKey(),
                'azure.cosmos.database.throughput-request-units'  : '1000',
                'azure.cosmos.database.throughput-auto-scale'     : 'true',
                'azure.cosmos.database.database-name'             : 'mydb'
        ]
    }
}
