package io.micronaut.data.azure


import com.azure.cosmos.CosmosClient
import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.cosmos.models.ExcludedPath
import com.azure.cosmos.models.IncludedPath
import com.azure.cosmos.models.IndexingMode
import com.azure.cosmos.models.IndexingPolicy
import com.azure.cosmos.models.PartitionKey
import com.azure.cosmos.models.ThroughputProperties
import com.azure.cosmos.models.UniqueKey
import com.azure.cosmos.models.UniqueKeyPolicy
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

    private static final int BOOK_TIME_TO_LIVE = 30 * 24 * 60 * 60
    private static final int FAMILY_TIME_TO_LIVE = 365 * 24 * 60 * 60

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
        def dbThroughputSettings = config.getThroughput()
        def throughputProperties = dbThroughputSettings != null ? dbThroughputSettings.createThroughputProperties() : null
        if (throughputProperties) {
            client.createDatabaseIfNotExists(config.databaseName, throughputProperties)
        } else {
            client.createDatabaseIfNotExists(config.databaseName)
        }
        def database = client.getDatabase(config.databaseName)
        def bookContainerProperties = new CosmosContainerProperties("cosmosbook", "/id")
        bookContainerProperties.setDefaultTimeToLiveInSeconds(BOOK_TIME_TO_LIVE)
        def bookIndexingPolicy = new IndexingPolicy()
        bookIndexingPolicy.setIncludedPaths(Arrays.asList(new IncludedPath("/*"), new IncludedPath("/title/*")))
        bookContainerProperties.setIndexingPolicy(bookIndexingPolicy)
        def bookUniqueKeyPolicy = new UniqueKeyPolicy()
        bookUniqueKeyPolicy.setUniqueKeys(Arrays.asList(new UniqueKey(Arrays.asList("/title", "/totalPages"))))
        bookContainerProperties.setUniqueKeyPolicy(bookUniqueKeyPolicy)
        database.createContainerIfNotExists(bookContainerProperties, ThroughputProperties.createManualThroughput(500))
        def familyContainerProperties = new CosmosContainerProperties("family", "/lastName")
        familyContainerProperties.setDefaultTimeToLiveInSeconds(FAMILY_TIME_TO_LIVE)
        def familyIndexingPolicy = new IndexingPolicy()
        familyIndexingPolicy.setIncludedPaths(Arrays.asList(new IncludedPath("/*"), new IncludedPath("/lastName/*")))
        familyIndexingPolicy.setExcludedPaths(Arrays.asList(new ExcludedPath("/address/*")))
        familyContainerProperties.setIndexingPolicy(familyIndexingPolicy)
        def familyUniqueKeyPolicy = new UniqueKeyPolicy()
        familyUniqueKeyPolicy.setUniqueKeys(Arrays.asList(new UniqueKey(Arrays.asList("/lastName", "/registered"))))
        familyContainerProperties.setUniqueKeyPolicy(familyUniqueKeyPolicy)
        database.createContainerIfNotExists(familyContainerProperties, ThroughputProperties.createManualThroughput(1100))
    }

    @Override
    Map<String, String> getDbInitProperties() {
        return Collections.emptyMap()
    }

    def "verify container properties"() {
        given:
            def client = context.getBean(CosmosClient)
            def config = context.getBean(CosmosDatabaseConfiguration)
            def database = client.getDatabase(config.databaseName)
            def bookContainer = database.getContainer("cosmosbook")
            def familyContainer = database.getContainer("family")
        when:
            def bookContainerProperties = bookContainer.read().getProperties()
            def bookThroughPut = bookContainer.readThroughput()
            def familyContainerProperties = familyContainer.read().getProperties()
            def familyThroughput = familyContainer.readThroughput()
        then:
            bookThroughPut.properties.autoscaleMaxThroughput == 0
            bookThroughPut.properties.manualThroughput == 500
            bookContainerProperties.defaultTimeToLiveInSeconds == BOOK_TIME_TO_LIVE
            bookContainerProperties.uniqueKeyPolicy.uniqueKeys.size() == 1
            bookContainerProperties.uniqueKeyPolicy.uniqueKeys[0].paths == Arrays.asList("/title", "/totalPages")
            bookContainerProperties.indexingPolicy.indexingMode == IndexingMode.CONSISTENT
            bookContainerProperties.indexingPolicy.includedPaths == Arrays.asList(new IncludedPath("/*"), new IncludedPath("/title/*"))
            // by default _etag is excluded
            bookContainerProperties.indexingPolicy.excludedPaths.size() == 1
            familyThroughput.properties.autoscaleMaxThroughput == 0
            familyThroughput.properties.manualThroughput == 1100
            familyContainerProperties.defaultTimeToLiveInSeconds == FAMILY_TIME_TO_LIVE
            familyContainerProperties.uniqueKeyPolicy.uniqueKeys.size() == 1
            familyContainerProperties.uniqueKeyPolicy.uniqueKeys[0].paths == Arrays.asList("/lastName", "/registered")
            familyContainerProperties.indexingPolicy.includedPaths == Arrays.asList(new IncludedPath("/*"), new IncludedPath("/lastName/*"))
            familyContainerProperties.indexingPolicy.excludedPaths.size() == 2
            familyContainerProperties.indexingPolicy.excludedPaths.contains(new ExcludedPath("/address/*"))
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
