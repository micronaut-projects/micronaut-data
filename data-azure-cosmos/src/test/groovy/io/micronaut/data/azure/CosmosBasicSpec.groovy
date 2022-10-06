package io.micronaut.data.azure

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosContainer
import com.azure.cosmos.CosmosDatabase
import com.azure.cosmos.models.CosmosContainerProperties
import com.azure.cosmos.models.CosmosContainerResponse
import com.azure.cosmos.models.CosmosDatabaseResponse
import com.azure.cosmos.models.CosmosItemRequestOptions
import com.azure.cosmos.models.CosmosQueryRequestOptions
import com.azure.cosmos.models.PartitionKey
import com.azure.cosmos.models.ThroughputProperties
import com.azure.cosmos.util.CosmosPagedIterable
import com.fasterxml.jackson.databind.node.ObjectNode
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.data.azure.entities.Address
import io.micronaut.data.azure.entities.Child
import io.micronaut.data.azure.entities.CosmosBook
import io.micronaut.data.azure.entities.Family
import io.micronaut.data.azure.repositories.CosmosBookRepository
import io.micronaut.data.azure.repositories.FamilyRepository
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration
import io.micronaut.data.cosmos.config.StorageUpdatePolicy
import io.micronaut.serde.Decoder
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.JacksonDecoder
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

@IgnoreIf({ env["GITHUB_WORKFLOW"] })
class CosmosBasicSpec extends Specification implements AzureCosmosTestProperties {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    CosmosBookRepository bookRepository = context.getBean(CosmosBookRepository)

    FamilyRepository familyRepository = context.getBean(FamilyRepository)

    def "test find by id"() {
        given:
            def book = new CosmosBook()
            book.id = UUID.randomUUID().toString()
            book.title = "The Stand"
            book.totalPages = 1000
            bookRepository.save(book)
        when:
            def optBook = bookRepository.queryById(book.id, new PartitionKey(book.id))
        then:
            optBook.present
            optBook.get().id == book.id
            optBook.get().totalPages == book.totalPages
        when:
            def nonExistingId = UUID.randomUUID().toString()
            optBook = bookRepository.queryById(nonExistingId, new PartitionKey(nonExistingId))
        then:
            !optBook.present
        when:
            def loadedBook = bookRepository.queryById(book.id)
        then:
            loadedBook
            loadedBook.totalPages == book.totalPages
    }

    def "test find with query"() {
        given:
            def book1 = new CosmosBook()
            book1.id = UUID.randomUUID().toString()
            book1.title = "The Stand"
            book1.totalPages = 1000
            def book2 = new CosmosBook()
            book2.id = UUID.randomUUID().toString()
            book2.title = "Ice And Fire"
            book2.totalPages = 200
        when:
            bookRepository.save(book1)
            bookRepository.save(book2)
            def optionalBook = bookRepository.queryById(book1.id)
        then:
            optionalBook
            optionalBook.title == "The Stand"
        when:
            def foundBook = bookRepository.searchByTitle("Ice And Fire")
        then:
            foundBook
            foundBook.title == "Ice And Fire"
    }

    def "save and load family in cosmos repo"() {
        given:
            def family = new Family()
            family.id = "AndersenFamily"
            family.lastName = "Andersen"
            def address = new Address()
            address.city = "Seattle"
            address.county = "King"
            address.state = "WA"
            family.address = address
            def child1 = new Child()
            child1.firstName = "Henriette Thaulow"
            child1.gender = "female"
            child1.grade = 5
            family.children.add(child1)
            familyRepository.save(family)
        when:
            def optFamily = familyRepository.findById(family.id)
        then:
            optFamily.present
            optFamily.get().id == family.id
            optFamily.get().children.size() > 0
            optFamily.get().address
    }

    def "should get cosmos client"() {
        when:
            SerdeRegistry registry = context.getBean(SerdeRegistry)
            CosmosClient client = context.getBean(CosmosClient)
            CosmosDatabaseResponse databaseResponse = client.createDatabaseIfNotExists("mydb")
            CosmosDatabase database = client.getDatabase(databaseResponse.getProperties().getId())

            CosmosContainerProperties containerProperties =
                    new CosmosContainerProperties("book", "/null");

            // Provision throughput
            ThroughputProperties throughputProperties = ThroughputProperties.createManualThroughput(400);

            CosmosContainerResponse containerResponse = database.createContainerIfNotExists(containerProperties, throughputProperties);
            CosmosContainer container = database.getContainer(containerResponse.getProperties().getId());

            XBook book = new XBook()
            book.id = UUID.randomUUID()
            book.name = "Ice & Fire"

            def encoderContext = registry.newEncoderContext(Object)
            def type = Argument.of(XBook)

            def item = container.createItem(book, PartitionKey.NONE, new CosmosItemRequestOptions())
            System.out.println("XXX " + item.getStatusCode())

            CosmosPagedIterable<ObjectNode> filteredFamilies = container.queryItems("SELECT * FROM c", new CosmosQueryRequestOptions(), ObjectNode.class);

            if (filteredFamilies.iterator().hasNext()) {
                ObjectNode b = filteredFamilies.iterator().next();

                def parser = b.traverse()
                if (!parser.hasCurrentToken()) {
                    parser.nextToken()
                }
                final Decoder decoder = JacksonDecoder.create(parser, Object);
                Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
                Deserializer<XBook> typeDeserializer = registry.findDeserializer(type);
                Deserializer<XBook> deserializer = typeDeserializer.createSpecific(decoderContext, type);

                XBook des = deserializer.deserialize(
                        decoder,
                        decoderContext,
                        type
                );

                System.out.println("BOOK: " + b)
                System.out.println("VVV " + des)
            }

        then:
            true
    }

    def "test configuration"() {
        given:
            def config = context.getBean(CosmosDatabaseConfiguration)

        expect:
            config.databaseName == 'mydb'
            config.throughput.autoScale
            config.throughput.requestUnits == 1000
            config.updatePolicy == StorageUpdatePolicy.CREATE_IF_NOT_EXISTS

            config.containers
            config.containers.size() == 2
            def familyContainer = config.containers.find {it -> it.containerName == "family"}
            familyContainer.containerName == "family"
            familyContainer.partitionKeyPath == "/lastName"
            !familyContainer.throughput.autoScale
            familyContainer.throughput.requestUnits == 1000

            def cosmosBookContainer = config.containers.find {it -> it.containerName == "cosmosbook"}
            cosmosBookContainer.containerName == "cosmosbook"
            cosmosBookContainer.partitionKeyPath == "/id"
            !cosmosBookContainer.throughput.autoScale
            cosmosBookContainer.throughput.requestUnits == 1200
    }


}
