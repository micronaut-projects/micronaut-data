package io.micronaut.data.azure

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosContainer
import com.azure.cosmos.CosmosDatabase
import com.azure.cosmos.CosmosDiagnostics
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
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.core.util.CollectionUtils
import io.micronaut.core.util.StringUtils
import io.micronaut.data.azure.entities.Address
import io.micronaut.data.azure.entities.GenderAware
import io.micronaut.data.azure.entities.ItemPrice
import io.micronaut.data.azure.entities.Child
import io.micronaut.data.azure.entities.CosmosBook
import io.micronaut.data.azure.entities.Family
import io.micronaut.data.azure.entities.Pet
import io.micronaut.data.azure.entities.PetType
import io.micronaut.data.azure.entities.UUIDEntity
import io.micronaut.data.azure.entities.User
import io.micronaut.data.azure.entities.XBook
import io.micronaut.data.azure.repositories.CosmosBookDtoRepository
import io.micronaut.data.azure.repositories.CosmosBookRepository
import io.micronaut.data.azure.repositories.FamilyRepository
import io.micronaut.data.azure.repositories.UUIDEntityRepository
import io.micronaut.data.azure.repositories.UserRepository
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration
import io.micronaut.data.cosmos.config.StorageUpdatePolicy
import io.micronaut.data.cosmos.operations.CosmosDiagnosticsProcessor
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.model.Pageable
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.serde.Decoder
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.JacksonDecoder
import jakarta.inject.Singleton
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaUpdate
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.data.azure.repositories.FamilyRepository.Specifications.idsIn
import static io.micronaut.data.azure.repositories.FamilyRepository.Specifications.idsInAndNotIn
import static io.micronaut.data.azure.repositories.FamilyRepository.Specifications.idsNotIn
import static io.micronaut.data.azure.repositories.FamilyRepository.Specifications.lastNameEquals
import static io.micronaut.data.azure.repositories.FamilyRepository.Specifications.registeredEquals
import static io.micronaut.data.azure.repositories.FamilyRepository.Specifications.childrenArrayContainsGender

@IgnoreIf({ env["GITHUB_WORKFLOW"] })
class CosmosBasicSpec extends Specification implements AzureCosmosTestProperties {

    private static final Family ANDERSEN_FAMILY = createAndersenFamily()
    private static final Family WAKEFIELD_FAMILY = createWakefieldFamily()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    CosmosBookRepository bookRepository = context.getBean(CosmosBookRepository)

    CosmosBookDtoRepository bookDtoRepository = context.getBean(CosmosBookDtoRepository)

    FamilyRepository familyRepository = context.getBean(FamilyRepository)

    UUIDEntityRepository uuidEntityRepository = context.getBean(UUIDEntityRepository)

    UserRepository userRepository = context.getBean(UserRepository)

    static Family createAndersenFamily() {
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
        def pet1 = new Pet()
        pet1.givenName = "Sadik"
        pet1.type = PetType.CAT
        child1.pets.add(pet1)
        def pet2 = new Pet()
        pet2.givenName = "Leo"
        pet2.type = PetType.DOG
        child1.pets.add(pet2)
        family.children.add(child1)
        return family
    }

    static Family createWakefieldFamily() {
        def family = new Family()
        family.id = "WakefieldFamily"
        family.lastName = "Wakefield"
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
        pet1.type = PetType.HAMSTER
        child2.pets.add(pet1)
        family.children.add(child2)
        return family
    }

    void saveSampleFamilies() {
        familyRepository.save(createAndersenFamily())
        familyRepository.save(createWakefieldFamily())
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
            book.version
            optBook.present
            optBook.get().id == book.id
            optBook.get().totalPages == book.totalPages
            book.version == optBook.get().version
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

    def "test find with query and other entity fields and annotations"() {
        given:
            def book1 = new CosmosBook()
            book1.title = "The Stand"
            book1.totalPages = 1000
            book1.itemPrice = new ItemPrice(199.99)
            def book2 = new CosmosBook()
            book2.title = "Ice And Fire"
            book2.totalPages = 200
        when:
            bookRepository.save(book1)
            bookRepository.save(book2)
            def notLoadedBook = bookRepository.queryById(UUID.randomUUID().toString())
            def loadedBook1 = bookRepository.queryById(book1.id)
            def loadedBook2 = bookRepository.queryById(book2.id)
            def version1 = loadedBook1.version
            def version2 = loadedBook2.version
        then:
            book1.version
            book2.version
            !notLoadedBook
            loadedBook1
            loadedBook1.title == "The Stand"
            loadedBook1.created
            loadedBook1.lastUpdated
            loadedBook1.itemPrice
            loadedBook1.itemPrice.price == Double.valueOf(199.99)
            loadedBook2
            loadedBook2.title == "Ice And Fire"
            loadedBook2.created
            loadedBook2.lastUpdated
            !loadedBook2.itemPrice
            version1
            version2
        when:
            def foundBook = bookRepository.searchByTitle("Ice And Fire")
        then:
            foundBook
            foundBook.title == "Ice And Fire"
            foundBook.version == version2
        when:
            def totalPages = loadedBook1.totalPages
            loadedBook1.totalPages = totalPages + 1
            bookRepository.update(loadedBook1)
            foundBook = bookRepository.findById(loadedBook1.id).get()
            loadedBook1.version != version1
        then:
            foundBook.id == loadedBook1.id
            foundBook.totalPages == totalPages + 1
            foundBook.created == loadedBook1.created
            foundBook.lastUpdated != loadedBook1.lastUpdated
            foundBook.version != version1
        when:
            def latestVersion = foundBook.version
            foundBook.version = UUID.randomUUID().toString()
            bookRepository.update(foundBook)
        then:
            thrown(OptimisticLockException)
        when:
            bookRepository.delete(foundBook)
        then:
            thrown(OptimisticLockException)
        when:
            foundBook.version = latestVersion
            bookRepository.update(foundBook)
            foundBook = bookRepository.findById(foundBook.id).get()
            bookRepository.delete(foundBook)
        then:
            noExceptionThrown()
        when:
            def newBook1 = new CosmosBook("A Game of Thrones", 900)
            def newBook2 = new CosmosBook("A Clash of Kings", 1100)
            def savedNewBooks = CollectionUtils.iterableToList(bookRepository.saveAll(Arrays.asList(newBook1, newBook2)))
        then:"Make sure id and versions are assigned in multi save"
            savedNewBooks.size() == 2
            savedNewBooks[0].id == newBook1.id
            savedNewBooks[1].id == newBook2.id
            newBook1.id
            newBook1.version
            newBook2.id
            newBook2.version
        when:
            def loadedNewBook1 = bookRepository.findById(newBook1.id).get()
            def loadedNewBook2 = bookRepository.findById(newBook2.id).get()
        then:
            loadedNewBook1.version == newBook1.version
            loadedNewBook2.version == newBook2.version
        cleanup:
            bookRepository.deleteAll()
    }

    def "crud family in cosmos repo"() {
        given:
            saveSampleFamilies()
        when:
            def families = familyRepository.childrenArrayContainsGender(new AbstractMap.SimpleImmutableEntry<String, Object>("gender", "male"))
            def families1 = familyRepository.findAll(childrenArrayContainsGender(new GenderAware("male")))
        then:
            families.size() == 1
            families1.size() == 1
        when:
            def optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id)
            def optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id)
        then:
            optFamily1.present
            optFamily1.get().id == ANDERSEN_FAMILY.id
            optFamily1.get().children.size() == ANDERSEN_FAMILY.children.size()
            optFamily1.get().address
            optFamily2.present
            optFamily2.get().id == WAKEFIELD_FAMILY.id
            optFamily2.get().children.size() == WAKEFIELD_FAMILY.children.size()
            optFamily2.get().address
        when:
            families = familyRepository.findByLastNameLike("Ander%")
        then:
            families.size() > 0
            families[0].id == ANDERSEN_FAMILY.id
        when:
            families = familyRepository.findByChildrenPetsType(PetType.CAT)
        then:
            families.size() > 0
            families[0].id == ANDERSEN_FAMILY.id
        when:
            families = familyRepository.findByChildrenFirstNameIn(Arrays.asList(ANDERSEN_FAMILY.children[0].firstName, WAKEFIELD_FAMILY.children[0].firstName))
        then:
            families.size() == 2
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
            optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id)
        then:
            optFamily2.present
            !optFamily2.get().registeredDate
            !optFamily2.get().registered
        when:
            def exists = familyRepository.existsById(ANDERSEN_FAMILY.id)
        then:
            exists
        when:
            exists = familyRepository.existsById(UUID.randomUUID().toString())
        then:
            !exists
        when:
            exists = familyRepository.existsByIdAndRegistered(ANDERSEN_FAMILY.id, true)
        then:
            !exists
        when:
            exists = familyRepository.existsByIdAndRegistered(ANDERSEN_FAMILY.id, false)
        then:
            exists
        when:
            def updated = familyRepository.updateAll(new UpdateSpecification<Family>() {
                @Override
                Predicate toPredicate(Root<Family> root, CriteriaUpdate<?> query, CriteriaBuilder criteriaBuilder) {
                    query.set("registered", true)
                    return criteriaBuilder.equal(root.get("registered"), false)
                }
            })
        then:
            updated == 2
            familyRepository.count(registeredEquals(true)) == 2
            familyRepository.count(registeredEquals(false)) == 0
        when:
            def cnt = familyRepository.count()
        then:
            cnt >= 2
        when:
            cnt = familyRepository.countByRegistered(true)
        then:
            cnt >= 2
        when:"Using raw query for update is not supported"
            familyRepository.updateLastName(ANDERSEN_FAMILY.id, "New Last Name")
        then:
            thrown(IllegalStateException)
        when:
            def address = optFamily1.get().getAddress()
            address.city = "LA"
            address.state = "CA"
            address.county = "Los Angeles"
            familyRepository.updateAddress(ANDERSEN_FAMILY.id, address)
            optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id)
        then:
            optFamily1.get().address.county == "Los Angeles"
            optFamily1.get().address.city == "LA"
            optFamily1.get().address.state == "CA"
        when:
            familyRepository.updateRegistered(ANDERSEN_FAMILY.id, true)
            optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id)
        then:
            optFamily1.get().registered
        when:
            familyRepository.updateRegistered(WAKEFIELD_FAMILY.id, true, new PartitionKey(optFamily2.get().getLastName()))
            optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id)
        then:
            optFamily2.get().registered
        when:
            optFamily1.get().address.state = "FL"
            familyRepository.update(optFamily1.get())
            optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id)
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
            optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id)
            optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id)
        then:
            optFamily2.get().children.size() == WAKEFIELD_FAMILY.children.size() + 1
            optFamily1.get().address.state == "NY"
        when:"Using raw query for delete is not supported"
            familyRepository.deleteByRegistered(false)
        then:
            thrown(IllegalStateException)
        when:
            familyRepository.deleteById(WAKEFIELD_FAMILY.id, new PartitionKey(optFamily2.get().lastName))
            optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id)
        then:
            !optFamily2.present
        when:
            familyRepository.delete(familyRepository.findById(ANDERSEN_FAMILY.id).get())
            optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id)
        then:
            !optFamily1.present
        when:
            familyRepository.saveAll(Arrays.asList(createAndersenFamily(), createWakefieldFamily()))
            optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id)
            optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id)
        then:
            optFamily1.present
            optFamily2.present
        when:
            familyRepository.deleteAll()
            optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id)
            optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id)
        then:
            !optFamily1.present
            !optFamily2.present
        when:
            saveSampleFamilies()
            optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id)
            optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id)
        then:
            optFamily1.present
            optFamily2.present
        when:
            familyRepository.deleteAll(Arrays.asList(optFamily1.get(), optFamily2.get()))
            optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id)
            optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id)
        then:
            !optFamily1.present
            !optFamily2.present
        when:
            familyRepository.save(createAndersenFamily())
            def lastName = familyRepository.findById(ANDERSEN_FAMILY.id).get().lastName
            familyRepository.deleteByLastName(lastName, new PartitionKey(lastName))
            optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id)
        then:
            !optFamily1.present
        cleanup:
            familyRepository.deleteAll()
    }

    void "test criteria" () {
        when:
            saveSampleFamilies()
        then:
            familyRepository.findAll(childrenArrayContainsGender(new GenderAware("female"))).size() == 2
            familyRepository.findOne(lastNameEquals("Andersen")).isPresent()
            !familyRepository.findOne(lastNameEquals(UUID.randomUUID().toString())).isPresent()
            familyRepository.findAll(idsIn(ANDERSEN_FAMILY.id, WAKEFIELD_FAMILY.id)).size() == 2
            familyRepository.findAll(idsIn(ANDERSEN_FAMILY.id)).size() == 1
            familyRepository.findByIdIn(Arrays.asList(ANDERSEN_FAMILY.id, WAKEFIELD_FAMILY.id)).size() == 2
            familyRepository.findByIdIn(Arrays.asList(ANDERSEN_FAMILY.id)).size() == 1
            familyRepository.findAll(idsNotIn(ANDERSEN_FAMILY.id)).size() == 1
            familyRepository.findByIdNotIn(Arrays.asList(ANDERSEN_FAMILY.id)).size() == 1
            familyRepository.findAll(idsInAndNotIn(Arrays.asList(ANDERSEN_FAMILY.id, WAKEFIELD_FAMILY.id), Arrays.asList(UUID.randomUUID().toString()))).size() == 2
            familyRepository.findAll(idsInAndNotIn(Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString()), Arrays.asList(ANDERSEN_FAMILY.id, WAKEFIELD_FAMILY.id))).size() == 0
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
            ))
        when:
            def slice = bookRepository.list(Pageable.from(0, 3))
            def resultList = bookRepository.findByTotalPagesGreaterThan(500, Pageable.from(0, 3))
        then:
            slice.numberOfElements == 3
            resultList.size() == 3
        cleanup:
            bookRepository.deleteAll()
    }

    def "entity with custom id field name and type"() {
        given:
            def entity1 = new UUIDEntity()
            entity1.name = "entity1"
            def entity2 = new UUIDEntity()
            entity2.name = "entity2"
            entity2.number = UUID.randomUUID()
            uuidEntityRepository.saveAll(Arrays.asList(entity1, entity2))
            def user1 = new User()
            user1.userId = 1L
            user1.userName = "user1"
            userRepository.save(user1)
            def user2 = new User()
            user2.userId = 2L
            user2.userName = "user2"
            userRepository.save(user2)
        when:
            def entities = uuidEntityRepository.findAll()
            def users = userRepository.findAll()
        then:
            entities.size() == 2
            users.size() == 2
        when:
            def optEntity1 = uuidEntityRepository.findById(entity1.number)
            def optEntity2 = uuidEntityRepository.findById(entity2.number)
            def optEntity3 = uuidEntityRepository.findById(UUID.randomUUID())
            def optUser1 = userRepository.findById(user1.userId)
            def optUser2 = userRepository.findById(user2.userId)
            def optUser3 = userRepository.findById(Long.MAX_VALUE)
            def foundEntity1 = uuidEntityRepository.queryByNumber(entity1.number)
            def foundEntityWithPartitionKey1 = uuidEntityRepository.queryByNumber(entity1.number, new PartitionKey(entity1.name))
            def foundUser1 = userRepository.queryByUserId(user1.userId)
            def foundUserWithPartitionKey1 = userRepository.queryByUserId(user1.userId, new PartitionKey(user1.userName))
        then:
            optEntity1.present
            optEntity2.present
            !optEntity3.present
            optUser1.present
            optUser2.present
            !optUser3.present
            foundEntity1.present
            foundEntityWithPartitionKey1.present
            foundUser1.present
            foundUserWithPartitionKey1.present
        cleanup:
            uuidEntityRepository.deleteAll()
            userRepository.deleteAll()
    }

    def "should get cosmos client"() {
        when:
            SerdeRegistry registry = context.getBean(SerdeRegistry)
            CosmosClient client = context.getBean(CosmosClient)
            CosmosDatabaseResponse databaseResponse = client.createDatabaseIfNotExists("mydb")
            CosmosDatabase database = client.getDatabase(databaseResponse.getProperties().getId())

            CosmosContainerProperties containerProperties =
                    new CosmosContainerProperties("book", "/null")

            // Provision throughput
            ThroughputProperties throughputProperties = ThroughputProperties.createManualThroughput(400)

            CosmosContainerResponse containerResponse = database.createContainerIfNotExists(containerProperties, throughputProperties)
            CosmosContainer container = database.getContainer(containerResponse.getProperties().getId())

            XBook book = new XBook()
            book.id = UUID.randomUUID()
            book.name = "Ice & Fire"

            def type = Argument.of(XBook)

            def item = container.createItem(book, PartitionKey.NONE, new CosmosItemRequestOptions())
            System.out.println("XXX " + item.getStatusCode())

            CosmosPagedIterable<ObjectNode> filteredFamilies = container.queryItems("SELECT * FROM c", new CosmosQueryRequestOptions(), ObjectNode.class)

            if (filteredFamilies.iterator().hasNext()) {
                ObjectNode b = filteredFamilies.iterator().next()

                def parser = b.traverse()
                if (!parser.hasCurrentToken()) {
                    parser.nextToken()
                }
                final Decoder decoder = JacksonDecoder.create(parser, Object)
                Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null)
                Deserializer<XBook> typeDeserializer = registry.findDeserializer(type)
                Deserializer<XBook> deserializer = typeDeserializer.createSpecific(decoderContext, type)

                XBook des = deserializer.deserialize(
                        decoder,
                        decoderContext,
                        type
                )

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

    @Requires(property = "spec.name", value = "CosmosBasicSpec")
    @Singleton
    static class LoggingCosmosDiagnosticsProcessor implements CosmosDiagnosticsProcessor {
        private static final Logger LOG = LoggerFactory.getLogger(CosmosBasicSpec)

        @Override
        void processDiagnostics(String operationName, CosmosDiagnostics cosmosDiagnostics, String activityId, double requestCharge) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Operation Name: {};\nDiagnostics: {};\nactivityId: {};\nrequestCharge: {}", cosmosDiagnostics, activityId, requestCharge)
            }
        }
    }
}
