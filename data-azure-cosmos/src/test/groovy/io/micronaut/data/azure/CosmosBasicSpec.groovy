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
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.data.azure.repositories.CosmosBookRepository
import io.micronaut.data.document.tck.entities.Book
import io.micronaut.serde.Decoder
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.JacksonDecoder
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CosmosBasicSpec extends Specification implements AzureCosmosTestProperties {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    def "test find by id"() {
        given:
            def bookRepository = context.getBean(CosmosBookRepository)
            Book book = new Book()
            book.id = UUID.randomUUID().toString()
            book.title = "The Stand"
            book.totalPages = 1000
        when:
            bookRepository.save(book)
            def optionalBook = bookRepository.queryById(book.id)
        then:
            optionalBook
    }

    def "test find with query"() {
        given:
            def bookRepository = context.getBean(CosmosBookRepository)
            Book book1 = new Book()
            book1.id = UUID.randomUUID().toString()
            book1.title = "The Stand"
            book1.totalPages = 1000
            Book book2 = new Book()
            book2.id = UUID.randomUUID().toString()
            book2.title = "Ice And Fire"
            book2.totalPages = 200
        when:
            bookRepository.save(book1)
            bookRepository.save(book2)
            def optionalBook = bookRepository.findById(book1.id)
        then:
            optionalBook.isPresent()
            optionalBook.get().title == "The Stand"
        when:
            def foundBook = bookRepository.searchByTitle("Ice And Fire")
        then:
            foundBook
            foundBook.title == "Ice And Fire"
    }

    def "should get cosmos client"() {
        when:
            SerdeRegistry registry = context.getBean(SerdeRegistry)
            ObjectMapper jacksonMapper = context.getBean(ObjectMapper)
            CosmosClient client = context.getBean(CosmosClient)
            CosmosDatabaseResponse databaseResponse = client.createDatabaseIfNotExists("mydb")
            CosmosDatabase database = client.getDatabase(databaseResponse.getProperties().getId())

            CosmosContainerProperties containerProperties =
                    new CosmosContainerProperties("book", "/lastName");

            // Provision throughput
            ThroughputProperties throughputProperties = ThroughputProperties.createManualThroughput(400);

            CosmosContainerResponse containerResponse = database.createContainerIfNotExists(containerProperties, throughputProperties);
            CosmosContainer container = database.getContainer(containerResponse.getProperties().getId());

            XBook book = new XBook()
            book.id = UUID.randomUUID()
            book.name = "Ice & Fire"

            def encoderContext = registry.newEncoderContext(Object)
            def type = Argument.of(XBook)

            def item = container.createItem(result, PartitionKey.NONE, new CosmosItemRequestOptions())
            System.out.println("XXX " + item.getStatusCode())

            CosmosPagedIterable<ObjectNode> filteredFamilies = container.queryItems("SELECT * FROM c", new CosmosQueryRequestOptions(), ObjectNode.class);

            if (filteredFamilies.iterator().hasNext()) {
                ObjectNode b = filteredFamilies.iterator().next();

                def parser = b.traverse()
                if (!parser.hasCurrentToken()) {
                    parser.nextToken()
                }
                final Decoder decoder = JacksonDecoder.create(parser, Object);

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

}
