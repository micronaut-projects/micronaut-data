package io.micronaut.data.azure


import com.azure.cosmos.CosmosClient
import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.cosmos.models.PartitionKey
import com.azure.cosmos.models.ThroughputProperties
import io.micronaut.context.ApplicationContext
import io.micronaut.data.azure.entities.CosmosBook
import io.micronaut.data.azure.repositories.CosmosBookRepository
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration
import io.micronaut.data.cosmos.config.StorageUpdatePolicy
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

/**
 * This test does not run db and containers initialization on context load (StorageUpdatePolicy = NONE)
 * so we simulate database pre-existence in setupSpec method and let tests run with db created this way.
 */
@IgnoreIf({ env["GITHUB_WORKFLOW"] })
class ExistingCosmosDbSpec extends Specification implements AzureCosmosTestProperties {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    CosmosBookRepository bookRepository = context.getBean(CosmosBookRepository)

    /**
     * We create db and containers in the setup ie simulating we have db created so our initializer won't be called.
     */
    void setupSpec() {
        def config = context.getBean(CosmosDatabaseConfiguration)
        def client = context.getBean(CosmosClient)
        def dbThroughputSettings = config.getThroughput();
        def throughputProperties = dbThroughputSettings != null ? dbThroughputSettings.createThroughputProperties() : null;
        if (throughputProperties) {
            client.createDatabaseIfNotExists(config.databaseName, throughputProperties)
        } else {
            client.createDatabaseIfNotExists(config.databaseName)
        }
        def database = client.getDatabase(config.databaseName)
        def bookContainerProperties = new CosmosContainerProperties("cosmosbook", "/id");
        database.createContainerIfNotExists(bookContainerProperties, ThroughputProperties.createManualThroughput(500))
        def familyContainerProperties = new CosmosContainerProperties("family", "/lastName");
        database.createContainerIfNotExists(familyContainerProperties, ThroughputProperties.createManualThroughput(1100))
    }

    @Override
    Map<String, String> getDbInitProperties() {
        return Collections.emptyMap()
    }

    def "test save and find book"() {
        given:
            def book = new CosmosBook()
            book.id = UUID.randomUUID().toString()
            book.title = "Book1"
            book.totalPages = 500
            bookRepository.save(book)
        when:
            def optBook = bookRepository.queryById(book.id, new PartitionKey(book.id))
        then:
            optBook.present
            optBook.get().id == book.id
            optBook.get().totalPages == book.totalPages
        when:
            def loadedBook = bookRepository.queryById(book.id)
        then:
            loadedBook
            loadedBook.totalPages == book.totalPages
    }

    def "test configuration"() {
        given:
            def config = context.getBean(CosmosDatabaseConfiguration)

        expect:
            config.databaseName == 'mydb'
            config.throughput.autoScale
            config.throughput.requestUnits == 1000
            config.updatePolicy == StorageUpdatePolicy.NONE

            !config.containers || config.containers.size() == 0
    }


}
