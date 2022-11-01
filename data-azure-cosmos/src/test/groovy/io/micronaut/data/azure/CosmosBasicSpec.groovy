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
import io.micronaut.core.util.StringUtils
import io.micronaut.data.azure.entities.Address
import io.micronaut.data.azure.entities.Child
import io.micronaut.data.azure.entities.CosmosBook
import io.micronaut.data.azure.entities.Family
import io.micronaut.data.azure.entities.Pet
import io.micronaut.data.azure.repositories.CosmosBookDtoRepository
import io.micronaut.data.azure.repositories.CosmosBookRepository
import io.micronaut.data.azure.repositories.FamilyRepository
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration
import io.micronaut.data.cosmos.config.StorageUpdatePolicy
import io.micronaut.data.model.Pageable
import io.micronaut.serde.Decoder
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.JacksonDecoder
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.data.azure.repositories.FamilyRepository.Specifications.idsIn
import static io.micronaut.data.azure.repositories.FamilyRepository.Specifications.idsNotIn
import static io.micronaut.data.azure.repositories.FamilyRepository.Specifications.lastNameEquals


@IgnoreIf({ env["GITHUB_WORKFLOW"] })
class CosmosBasicSpec extends Specification implements AzureCosmosTestProperties {

    private static final String FAMILY1_ID = "AndersenFamily"
    private static final String FAMILY2_ID = "WakefieldFamily"

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    CosmosBookRepository bookRepository = context.getBean(CosmosBookRepository)

    CosmosBookDtoRepository bookDtoRepository = context.getBean(CosmosBookDtoRepository)

    FamilyRepository familyRepository = context.getBean(FamilyRepository)

    Family createSampleFamily1() {
        def family = new Family()
        family.id = FAMILY1_ID
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
        def pet1 = new Pet()
        pet1.givenName = "Sadik"
        pet1.type = "cat"
        child1.pets.add(pet1)
        def pet2 = new Pet()
        pet2.givenName = "Leo"
        pet2.type = "dog"
        child1.pets.add(pet2)
        family.children.add(child1)
        return family
    }

    Family createSampleFamily2() {
        def family = new Family()
        family.id = FAMILY2_ID
        family.lastName = "Johnson"
        def address = new Address()
        address.city = "NY"
        address.county = "Manhattan"
        address.state = "NY"
        family.address = address
        def child1 = new Child()
        child1.firstName = "Merriam"
        child1.gender = "female"
        child1.grade = 6
        family.children.add(child1)
        def child2 = new Child()
        child2.firstName = "Luke"
        child2.gender = "male"
        child2.grade = 8
        def pet1 = new Pet()
        pet1.givenName = "Robbie"
        pet1.type = "hamster"
        child2.pets.add(pet1)
        family.children.add(child2)
        return family
    }

    void saveSampleFamilies() {
        def family1 = createSampleFamily1()
        familyRepository.save(family1)
        def family2 = createSampleFamily2()
        familyRepository.save(family2)
    }

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
        cleanup:
            bookRepository.deleteAll()
    }

    def "test find with query"() {
        given:
            def book1 = new CosmosBook()
            book1.title = "The Stand"
            book1.totalPages = 1000
            def book2 = new CosmosBook()
            book2.title = "Ice And Fire"
            book2.totalPages = 200
        when:
            bookRepository.save(book1)
            bookRepository.save(book2)
            def notLoadedBook = bookRepository.queryById(UUID.randomUUID().toString())
            def loadedBook1 = bookRepository.queryById(book1.id)
            def loadedBook2 = bookRepository.queryById(book2.id)
        then:
            !notLoadedBook
            loadedBook1
            loadedBook1.title == "The Stand"
            loadedBook1.created
            loadedBook1.lastUpdated
            loadedBook2
            loadedBook2.title == "Ice And Fire"
            loadedBook2.created
            loadedBook2.lastUpdated
        when:
            def foundBook = bookRepository.searchByTitle("Ice And Fire")
        then:
            foundBook
            foundBook.title == "Ice And Fire"
        when:
            def totalPages = loadedBook1.totalPages
            loadedBook1.totalPages = totalPages + 1
            bookRepository.update(loadedBook1)
            foundBook = bookRepository.findById(loadedBook1.id).get()
        then:
            foundBook.id == loadedBook1.id
            foundBook.totalPages == totalPages + 1
            foundBook.created == loadedBook1.created
            foundBook.lastUpdated != loadedBook1.lastUpdated
        cleanup:
            bookRepository.deleteAll()
    }

    def "crud family in cosmos repo"() {
        given:
            saveSampleFamilies()
        when:
            def optFamily1 = familyRepository.findById(FAMILY1_ID)
            def optFamily2 = familyRepository.findById(FAMILY2_ID)
        then:
            optFamily1.present
            optFamily1.get().id == FAMILY1_ID
            optFamily1.get().children.size() == 1
            optFamily1.get().address
            optFamily2.present
            optFamily2.get().id == FAMILY2_ID
            optFamily2.get().children.size() == 2
            optFamily2.get().address
        when:
            def families = familyRepository.findByLastNameLike("Ander%")
        then:
            families.size() > 0
            families[0].id == FAMILY1_ID
        when:
            families = familyRepository.findByChildrenPetsType("cat")
        then:
            families.size() > 0
            families[0].id == FAMILY1_ID
        when:
            def children = familyRepository.findChildrenByChildrenPetsGivenName("Robbie")
        then:
            children.size() > 0
            children[0].firstName == "Luke"
        when:
            def address1 = optFamily1.get().address
            families = familyRepository.findByAddressStateAndAddressCityOrderByAddressCity(address1.state, address1.city)
        then:
            families.size() > 0
        when:
            def lastOrderedLastName = familyRepository.lastOrderedLastName()
        then:
            StringUtils.isNotEmpty(lastOrderedLastName)
        when:
            optFamily2.get().registeredDate = new Date()
            familyRepository.update(optFamily2.get())
            def lastOrderedRegisteredDate = familyRepository.lastOrderedRegisteredDate()
        then:
            lastOrderedRegisteredDate
            lastOrderedRegisteredDate == optFamily2.get().registeredDate
        when:
            familyRepository.updateByAddressCounty(optFamily2.get().address.county, false, null)
            optFamily2 = familyRepository.findById(FAMILY2_ID)
        then:
            optFamily2.present
            !optFamily2.get().registeredDate
            !optFamily2.get().registered
        when:
            def exists = familyRepository.existsById(FAMILY1_ID)
        then:
            exists
        when:
            exists = familyRepository.existsById(UUID.randomUUID().toString())
        then:
            !exists
        when:
            exists = familyRepository.existsByIdAndRegistered(FAMILY1_ID, true)
        then:
            !exists
        when:
            exists = familyRepository.existsByIdAndRegistered(FAMILY1_ID, false)
        then:
            exists
        when:
            def cnt = familyRepository.count()
        then:
            cnt >= 2
        when:
            cnt = familyRepository.countByRegistered(false)
        then:
            cnt >= 2
        when:"Using raw query for update is not supported"
            familyRepository.updateLastName(FAMILY1_ID, "New Last Name")
        then:
            thrown(IllegalStateException)
        when:
            def address = optFamily1.get().getAddress()
            address.city = "LA"
            address.state = "CA"
            address.county = "Los Angeles"
            familyRepository.updateAddress(FAMILY1_ID, address)
            optFamily1 = familyRepository.findById(FAMILY1_ID)
        then:
            optFamily1.get().address.county == "Los Angeles"
            optFamily1.get().address.city == "LA"
            optFamily1.get().address.state == "CA"
        when:
            familyRepository.updateRegistered(FAMILY1_ID, true)
            optFamily1 = familyRepository.findById(FAMILY1_ID)
        then:
            optFamily1.get().registered
        when:
            familyRepository.updateRegistered(FAMILY2_ID, true, new PartitionKey(optFamily2.get().getLastName()))
            optFamily2 = familyRepository.findById(FAMILY2_ID)
        then:
            optFamily2.get().registered
        when:
            optFamily1.get().address.state = "FL"
            familyRepository.update(optFamily1.get())
            optFamily1 = familyRepository.findById(FAMILY1_ID)
        then:
            optFamily1.get().address.state == "FL"
        when:
            def newChild = new Child()
            newChild.grade = 1
            newChild.gender = "male"
            newChild.firstName = "Isaac"
            optFamily2.get().children.add(newChild)
            optFamily1.get().address.state = "NY"
            familyRepository.updateAll(Arrays.asList(optFamily1.get(), optFamily2.get()))
            optFamily1 = familyRepository.findById(FAMILY1_ID)
            optFamily2 = familyRepository.findById(FAMILY2_ID)
        then:
            optFamily2.get().children.size() == 3
            optFamily1.get().address.state == "NY"
        when:"Using raw query for delete is not supported"
            familyRepository.deleteByRegistered(false)
        then:
            thrown(IllegalStateException)
        when:
            familyRepository.deleteById(FAMILY2_ID, new PartitionKey(optFamily2.get().lastName))
            optFamily2 = familyRepository.findById(FAMILY2_ID)
        then:
            !optFamily2.present
        when:
            familyRepository.delete(familyRepository.findById(FAMILY1_ID).get())
            optFamily1 = familyRepository.findById(FAMILY1_ID)
        then:
            !optFamily1.present
        when:
            familyRepository.saveAll(Arrays.asList(createSampleFamily1(), createSampleFamily2()))
            optFamily1 = familyRepository.findById(FAMILY1_ID)
            optFamily2 = familyRepository.findById(FAMILY2_ID)
        then:
            optFamily1.present
            optFamily2.present
        when:
            familyRepository.deleteAll()
            optFamily1 = familyRepository.findById(FAMILY1_ID)
            optFamily2 = familyRepository.findById(FAMILY2_ID)
        then:
            !optFamily1.present
            !optFamily2.present
        when:
            saveSampleFamilies()
            optFamily1 = familyRepository.findById(FAMILY1_ID)
            optFamily2 = familyRepository.findById(FAMILY2_ID)
        then:
            optFamily1.present
            optFamily2.present
        when:
            familyRepository.deleteAll(Arrays.asList(optFamily1.get(), optFamily2.get()))
            optFamily1 = familyRepository.findById(FAMILY1_ID)
            optFamily2 = familyRepository.findById(FAMILY2_ID)
        then:
            !optFamily1.present
            !optFamily2.present
        when:
            familyRepository.save(createSampleFamily1())
            def lastName = familyRepository.findById(FAMILY1_ID).get().lastName
            familyRepository.deleteByLastName(lastName, new PartitionKey(lastName))
            optFamily1 = familyRepository.findById(FAMILY1_ID)
        then:
            !optFamily1.present
        cleanup:
            familyRepository.deleteAll()
    }

    void "test criteria" () {
        when:
            saveSampleFamilies()
        then:
            familyRepository.findOne(lastNameEquals("Andersen")).isPresent()
            !familyRepository.findOne(lastNameEquals(UUID.randomUUID().toString())).isPresent()
            familyRepository.findAll(idsIn(FAMILY1_ID, FAMILY2_ID)).size() == 2
            familyRepository.findAll(idsIn(FAMILY2_ID)).size() == 1
            familyRepository.findByIdIn(Arrays.asList(FAMILY1_ID, FAMILY2_ID)).size() == 2
            familyRepository.findByIdIn(Arrays.asList(FAMILY1_ID)).size() == 1
            familyRepository.findAll(idsNotIn(FAMILY1_ID)).size() == 1
            familyRepository.findByIdNotIn(Arrays.asList(FAMILY1_ID)).size() == 1
        cleanup:
            familyRepository.deleteAll()
    }

    void "test DTO entity retrieval"() {
        given:
            CosmosBook book = new CosmosBook()
            book.id = UUID.randomUUID().toString()
            book.title = "New Book"
            book.totalPages = 500
            bookRepository.save(book)
        when:
            def loadedBook = bookRepository.queryById(book.id)
        then:
            loadedBook
        when:
            def bookDto = bookDtoRepository.findById(book.id)
        then:
            bookDto.present
            bookDto.get().title == book.title
            bookDto.get().totalPages == 500
        when:
            def bookDtos = bookDtoRepository.findByTitleAndTotalPages(book.title, book.totalPages)
        then:
            bookDtos.size() > 0
        cleanup:
            bookRepository.deleteAll()
    }

    void "test pageable"() {
        given:
            bookRepository.saveAll(Arrays.asList(
                    new CosmosBook("The Stand", 1000),
                    new CosmosBook("The Shining", 600),
                    new CosmosBook("The Power of the Dog", 500),
                    new CosmosBook("The Border", 700),
                    new CosmosBook("Along Came a Spider", 300),
                    new CosmosBook("Pet Cemetery", 400),
                    new CosmosBook("A Game of Thrones", 900),
                    new CosmosBook("A Clash of Kings", 1100)
            ));
        when:
            def slice = bookRepository.list(Pageable.from(0, 3));
            def resultList = bookRepository.findByTotalPagesGreaterThan(500, Pageable.from(0, 3));
        then:
            slice.numberOfElements == 3
            resultList.size() == 3
        cleanup:
            bookRepository.deleteAll()
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
