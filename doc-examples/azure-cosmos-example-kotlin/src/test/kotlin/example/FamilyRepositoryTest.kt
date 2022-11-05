package example

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.models.*
import example.FamilyRepository.Specifications.idsIn
import example.FamilyRepository.Specifications.idsInAndNotIn
import example.FamilyRepository.Specifications.idsNotIn
import example.FamilyRepository.Specifications.lastNameEquals
import io.micronaut.context.BeanContext
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.util.CollectionUtils
import io.micronaut.core.util.StringUtils
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration
import io.micronaut.data.cosmos.config.StorageUpdatePolicy
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest
@DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*")
class FamilyRepositoryTest : AbstractAzureCosmosTest() {

    @Inject
    lateinit var familyRepository: FamilyRepository

    @Inject
    lateinit var beanContext: BeanContext

    /**
     * Create containers to simulate situation where users will use our library against an existing database.
     */
    @BeforeAll
    fun beforeAll() {
        val config = beanContext.getBean(
            CosmosDatabaseConfiguration::class.java
        )
        val client = beanContext.getBean(CosmosClient::class.java)
        val dbThroughputSettings = config.throughput
        val throughputProperties = dbThroughputSettings?.createThroughputProperties()
        if (throughputProperties != null) {
            client.createDatabaseIfNotExists(config.databaseName, throughputProperties)
        } else {
            client.createDatabaseIfNotExists(config.databaseName)
        }
        val database = client.getDatabase(config.databaseName)
        val familyContainerProperties = CosmosContainerProperties("family", "/lastName")
        familyContainerProperties.defaultTimeToLiveInSeconds = TIME_TO_LIVE
        val familyIndexingPolicy = IndexingPolicy()
        familyIndexingPolicy.includedPaths = listOf(IncludedPath("/*"), IncludedPath("/lastName/*"))
        familyIndexingPolicy.excludedPaths = listOf(ExcludedPath("/children/*"))
        familyContainerProperties.indexingPolicy = familyIndexingPolicy
        val familyUniqueKeyPolicy = UniqueKeyPolicy()
        familyUniqueKeyPolicy.uniqueKeys = listOf(UniqueKey(listOf("/lastName", "/registered")))
        familyContainerProperties.uniqueKeyPolicy = familyUniqueKeyPolicy
        database.createContainerIfNotExists(
            familyContainerProperties,
            ThroughputProperties.createManualThroughput(1100)
        )
    }

    @NonNull
    override fun getProperties(): Map<String, String> {
        val properties = super.getProperties()
        // We don't want to create or update db/containers during app init
        return properties + mapOf("azure.cosmos.database.update-policy" to StorageUpdatePolicy.NONE.name)
    }

    @AfterEach
    fun cleanup() {
        familyRepository.deleteAll()
    }

    @Test
    fun testCrud() {
        familyRepository.save(createAndersenFamily())
        familyRepository.save(createWakefieldFamily())

        var optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id!!)
        var optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id!!)
        assertTrue(optFamily1.isPresent)
        assertEquals(optFamily1.get().id, ANDERSEN_FAMILY.id)
        assertEquals(optFamily1.get().children.size, ANDERSEN_FAMILY.children.size)
        assertNotNull(optFamily1.get().address)
        assertNotNull(optFamily1.get().documentVersion)
        assertTrue(optFamily2.isPresent)
        assertEquals(optFamily2.get().id, WAKEFIELD_FAMILY.id)
        assertEquals(optFamily2.get().children.size, WAKEFIELD_FAMILY.children.size)
        assertNotNull(optFamily2.get().address)
        assertNotNull(optFamily2.get().documentVersion)

        var families = familyRepository.findByLastNameLike("Ander%")
        assertTrue(families.isNotEmpty())
        assertEquals(families[0].id, ANDERSEN_FAMILY.id)
        families = familyRepository.findByChildrenPetsType("cat")
        assertTrue(families.isNotEmpty())
        assertEquals(families[0].id, ANDERSEN_FAMILY.id)

        val children = familyRepository.findChildrenByChildrenPetsGivenName("Robbie")
        assertTrue(children.isNotEmpty())
        assertEquals("Luke", children[0].firstName)

        val address1 = optFamily1.get().address
        families =
            familyRepository.findByAddressStateAndAddressCityOrderByAddressCity(address1!!.state!!, address1.city!!)
        assertTrue(families.isNotEmpty())

        val lastOrderedLastName = familyRepository.lastOrderedLastName()
        assertTrue(StringUtils.isNotEmpty(lastOrderedLastName))

        optFamily2.get().registeredDate = Date()
        familyRepository.update(optFamily2.get())
        val lastOrderedRegisteredDate = familyRepository.lastOrderedRegisteredDate()
        assertNotNull(lastOrderedRegisteredDate)
        assertEquals(lastOrderedRegisteredDate, optFamily2.get().registeredDate)

        familyRepository.updateByAddressCounty(optFamily2.get().address!!.county!!, false, null)
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id!!)
        assertTrue(optFamily2.isPresent)
        assertNull(optFamily2.get().registeredDate)
        assertFalse(optFamily2.get().registered)

        var exists = familyRepository.existsById(ANDERSEN_FAMILY.id!!)
        assertTrue(exists)
        exists = familyRepository.existsById(UUID.randomUUID().toString())
        assertFalse(exists)
        exists = familyRepository.existsByIdAndRegistered(ANDERSEN_FAMILY.id!!, true)
        assertFalse(exists)
        exists = familyRepository.existsByIdAndRegistered(ANDERSEN_FAMILY.id!!, false)
        assertTrue(exists)

        var count = familyRepository.count()
        assertTrue(count >= 2)
        count = familyRepository.countByRegistered(false)
        assertTrue(count >= 2)

        // Using raw query for update is not supported
        try {
            familyRepository.updateLastName(ANDERSEN_FAMILY.id!!, "New Last Name")
        } catch (e: IllegalStateException) {
            e.message!!.contains("Cosmos Db does not support raw update queries")
        }

        val address = optFamily1.get().address
        assertNotNull(address)
        address!!.city = "LA"
        address.state = "CA"
        address.county = "Los Angeles"
        familyRepository.updateAddress(ANDERSEN_FAMILY.id!!, address)
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id!!)
        assertTrue(optFamily1.isPresent)
        assertEquals("Los Angeles", optFamily1.get().address!!.county)
        assertEquals("LA", optFamily1.get().address!!.city)
        assertEquals("CA", optFamily1.get().address!!.state)

        familyRepository.updateRegistered(ANDERSEN_FAMILY.id!!, true)
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id!!)
        assertTrue(optFamily1.isPresent)
        assertTrue(optFamily1.get().registered)

        familyRepository.updateRegistered(WAKEFIELD_FAMILY.id!!, true, PartitionKey(optFamily2.get().lastName))
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id!!)
        assertTrue(optFamily2.isPresent)
        assertTrue(optFamily2.get().registered)
        optFamily1.get().address!!.state = "FL"

        familyRepository.update(optFamily1.get())
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id!!)
        assertTrue(optFamily1.isPresent)
        assertEquals("FL", optFamily1.get().address!!.state!!)

        val newChild = Child()
        newChild.grade = 1
        newChild.gender = "male"
        newChild.firstName = "Isaac"
        optFamily2.get().children += newChild
        optFamily1.get().address!!.state = "NY"
        familyRepository.updateAll(listOf(optFamily1.get(), optFamily2.get()))
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id!!)
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id!!)
        assertTrue(optFamily2.isPresent)
        assertEquals(optFamily2.get().children.size, WAKEFIELD_FAMILY.children.size + 1)
        assertTrue(optFamily1.isPresent)
        assertEquals("NY", optFamily1.get().address!!.state)

        // Using raw query for delete is not supported
        try {
            familyRepository.deleteByRegistered(false)
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Cosmos Db does not support raw delete queries"))
        }

        familyRepository.deleteById(WAKEFIELD_FAMILY.id!!, PartitionKey(optFamily2.get().lastName))
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id!!)
        assertFalse(optFamily2.isPresent)
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id!!)
        assertTrue(optFamily1.isPresent)

        familyRepository.delete(optFamily1.get())
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id!!)
        assertFalse(optFamily1.isPresent)

        familyRepository.saveAll(listOf(createAndersenFamily(), createWakefieldFamily()))
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id!!)
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id!!)
        assertTrue(optFamily1.isPresent)
        assertTrue(optFamily2.isPresent)

        familyRepository.deleteAll()
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id!!)
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id!!)
        assertFalse(optFamily1.isPresent)
        assertFalse(optFamily2.isPresent)

        saveSampleFamilies()
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id!!)
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id!!)
        assertTrue(optFamily1.isPresent)
        assertTrue(optFamily2.isPresent)

        familyRepository.deleteAll(listOf(optFamily1.get(), optFamily2.get()))
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id!!)
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id!!)
        assertFalse(optFamily1.isPresent)
        assertFalse(optFamily2.isPresent)

        familyRepository.save(createAndersenFamily())
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id!!)
        assertTrue(optFamily1.isPresent)
        val lastName = optFamily1.get().lastName

        familyRepository.deleteByLastName(lastName!!, PartitionKey(lastName))
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id!!)
        assertFalse(optFamily1.isPresent)
    }

    @Test
    fun testCriteria() {
        saveSampleFamilies()
        assertTrue(familyRepository.findOne(lastNameEquals("Andersen")).isPresent)
        assertFalse(familyRepository.findOne(lastNameEquals(UUID.randomUUID().toString())).isPresent)
        assertEquals(2, familyRepository.findAll(idsIn(ANDERSEN_FAMILY.id!!, WAKEFIELD_FAMILY.id!!)).size)
        assertEquals(1, familyRepository.findAll(idsIn(ANDERSEN_FAMILY.id!!)).size)
        assertEquals(
            2,
            familyRepository.findByIdIn(listOf(ANDERSEN_FAMILY.id!!, WAKEFIELD_FAMILY.id!!)).size
        )
        assertEquals(1, familyRepository.findByIdIn(listOf(ANDERSEN_FAMILY.id!!)).size)
        assertEquals(1, familyRepository.findAll(idsNotIn(ANDERSEN_FAMILY.id!!)).size)
        assertEquals(1, familyRepository.findByIdNotIn(listOf(ANDERSEN_FAMILY.id!!)).size)
        assertEquals(
            2, familyRepository.findAll(
                idsInAndNotIn(
                    listOf(ANDERSEN_FAMILY.id!!, WAKEFIELD_FAMILY.id!!), listOf(
                        UUID.randomUUID().toString()
                    )
                )
            ).size
        )
        assertTrue(
            CollectionUtils.isEmpty(
                familyRepository.findAll(
                    idsInAndNotIn(
                        listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString()), listOf(
                            ANDERSEN_FAMILY.id!!, WAKEFIELD_FAMILY.id!!
                        )
                    )
                ) as Collection<*>?
            )
        )
    }

    private fun saveSampleFamilies() {
        familyRepository.save(createAndersenFamily())
        familyRepository.save(createWakefieldFamily())
    }

    companion object {
        private val ANDERSEN_FAMILY = createAndersenFamily()
        private val WAKEFIELD_FAMILY = createWakefieldFamily()
        private const val TIME_TO_LIVE = 365 * 24 * 60 * 60
        private fun createAndersenFamily(): Family {
            val family = Family()
            family.id = "AndersenFamily"
            family.lastName = "Andersen"
            val address = Address()
            address.city = "Seattle"
            address.county = "King"
            address.state = "WA"
            family.address = address
            val child1 = Child()
            child1.firstName = "Henriette Thaulow"
            child1.gender = "female"
            child1.grade = 5
            val pet1 = Pet()
            pet1.givenName = "Sadik"
            pet1.type = "cat"
            child1.pets += pet1
            val pet2 = Pet()
            pet2.givenName = "Leo"
            pet2.type = "dog"
            child1.pets += pet2
            family.children += child1
            return family
        }

        private fun createWakefieldFamily(): Family {
            val family = Family()
            family.id = "WakefieldFamily"
            family.lastName = "Wakefield"
            val address = Address()
            address.city = "NY"
            address.county = "Manhattan"
            address.state = "NY"
            family.address = address
            val child1 = Child()
            child1.firstName = "Merriam"
            child1.gender = "female"
            child1.grade = 6
            family.children += child1
            val child2 = Child()
            child2.firstName = "Luke"
            child2.gender = "male"
            child2.grade = 8
            val pet1 = Pet()
            pet1.givenName = "Robbie"
            pet1.type = "hamster"
            child2.pets += pet1
            family.children += child2
            return family
        }
    }
}
