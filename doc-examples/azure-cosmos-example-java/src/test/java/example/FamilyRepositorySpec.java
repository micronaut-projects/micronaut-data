package example;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.models.UniqueKey;
import com.azure.cosmos.models.UniqueKeyPolicy;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration;
import io.micronaut.data.cosmos.config.StorageUpdatePolicy;
import io.micronaut.data.cosmos.config.ThroughputSettings;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static example.FamilyRepository.Specifications.childrenArrayContainsGender;
import static example.FamilyRepository.Specifications.idsIn;
import static example.FamilyRepository.Specifications.idsInAndNotIn;
import static example.FamilyRepository.Specifications.idsNotIn;
import static example.FamilyRepository.Specifications.lastNameEquals;
import static example.FamilyRepository.Specifications.tagsContain;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest
@DisabledIfEnvironmentVariable(named = "GITHUB_WORKFLOW", matches = ".*")
class FamilyRepositorySpec extends AbstractAzureCosmosTest {

    private static final Family ANDERSEN_FAMILY = createAndersenFamily();
    private static final Family WAKEFIELD_FAMILY = createWakefieldFamily();

    private static final int TIME_TO_LIVE = 365 * 24 * 60 * 60;

    @Inject
    FamilyRepository familyRepository;

    @Inject
    BeanContext beanContext;

    /**
     * Create containers to simulate situation where users will use our library against an existing database.
     */
    @BeforeAll
    public void beforeAll() {
        CosmosDatabaseConfiguration config = beanContext.getBean(CosmosDatabaseConfiguration.class);
        CosmosClient client = beanContext.getBean(CosmosClient.class);
        ThroughputSettings dbThroughputSettings = config.getThroughput();
        ThroughputProperties throughputProperties = dbThroughputSettings != null ? dbThroughputSettings.createThroughputProperties() : null;
        if (throughputProperties != null) {
            client.createDatabaseIfNotExists(config.getDatabaseName(), throughputProperties);
        } else {
            client.createDatabaseIfNotExists(config.getDatabaseName());
        }
        CosmosDatabase database = client.getDatabase(config.getDatabaseName());
        CosmosContainerProperties familyContainerProperties = new CosmosContainerProperties("family", "/lastName");
        familyContainerProperties.setDefaultTimeToLiveInSeconds(TIME_TO_LIVE);
        IndexingPolicy familyIndexingPolicy = new IndexingPolicy();
        familyIndexingPolicy.setIncludedPaths(Arrays.asList(new IncludedPath("/*"), new IncludedPath("/lastName/*")));
        familyIndexingPolicy.setExcludedPaths(Collections.singletonList(new ExcludedPath("/children/*")));
        familyContainerProperties.setIndexingPolicy(familyIndexingPolicy);
        UniqueKeyPolicy familyUniqueKeyPolicy = new UniqueKeyPolicy();
        familyUniqueKeyPolicy.setUniqueKeys(Collections.singletonList(new UniqueKey(Arrays.asList("/lastName", "/registered"))));
        familyContainerProperties.setUniqueKeyPolicy(familyUniqueKeyPolicy);
        database.createContainerIfNotExists(familyContainerProperties, ThroughputProperties.createManualThroughput(1100));
    }

    @Override
    @NonNull
    public Map<String, String> getProperties() {
        Map<String, String> properties = super.getProperties();
        // We don't want to create or update db/containers during app init
        properties.put("azure.cosmos.database.update-policy", StorageUpdatePolicy.NONE.name());
        return properties;
    }

    @AfterEach
    public void cleanup() {
        familyRepository.deleteAll();
    }

    @Test
    void testCrud() {
        familyRepository.save(createAndersenFamily());
        familyRepository.save(createWakefieldFamily());

        Optional<Family> optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.getId());
        Optional<Family> optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.getId());
        assertTrue(optFamily1.isPresent());
        assertEquals(optFamily1.get().getId(), ANDERSEN_FAMILY.getId());
        assertEquals(optFamily1.get().getChildren().size(), ANDERSEN_FAMILY.getChildren().size());
        assertNotNull(optFamily1.get().getAddress());
        assertNotNull(optFamily1.get().getDocumentVersion());
        assertTrue(optFamily2.isPresent());
        assertEquals(optFamily2.get().getId(), WAKEFIELD_FAMILY.getId());
        assertEquals(optFamily2.get().getChildren().size(), WAKEFIELD_FAMILY.getChildren().size());
        assertNotNull(optFamily2.get().getAddress());
        assertNotNull(optFamily2.get().getDocumentVersion());

        List<Family> families = familyRepository.childrenArrayContainsGender(new AbstractMap.SimpleImmutableEntry<>("gender", "male"));
        assertEquals(1, families.size());

        families = familyRepository.findByLastNameLike("Ander%");
        assertTrue(families.size() > 0);
        assertEquals(families.get(0).getId(), ANDERSEN_FAMILY.getId());

        families = familyRepository.findByChildrenPetsType(PetType.CAT);
        assertTrue(families.size() > 0);
        assertEquals(families.get(0).getId(), ANDERSEN_FAMILY.getId());

        families = familyRepository.findByTagsArrayContains("customtag");
        assertEquals(1, families.size());
        assertEquals(families.get(0).getId(), ANDERSEN_FAMILY.getId());

        List<Child> children = familyRepository.findChildrenByChildrenPetsGivenName("Robbie");
        assertTrue(children.size() > 0);
        assertEquals("Luke", children.get(0).getFirstName());

        Address address1 = optFamily1.get().getAddress();
        families = familyRepository.findByAddressStateAndAddressCityOrderByAddressCity(address1.getState(), address1.getCity());
        assertTrue(families.size() > 0);

        String lastOrderedLastName = familyRepository.lastOrderedLastName();
        assertTrue(StringUtils.isNotEmpty(lastOrderedLastName));

        optFamily2.get().setRegisteredDate(new Date());
        optFamily2.get().setComment("some comment");
        familyRepository.update(optFamily2.get());
        Date lastOrderedRegisteredDate = familyRepository.lastOrderedRegisteredDate();
        assertNotNull(lastOrderedRegisteredDate);
        assertEquals(lastOrderedRegisteredDate, optFamily2.get().getRegisteredDate());

        familyRepository.updateByAddressCounty(optFamily2.get().getAddress().getCounty(), false, null);
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.getId());
        assertTrue(optFamily2.isPresent());
        assertNull(optFamily2.get().getRegisteredDate());
        assertFalse(optFamily2.get().isRegistered());
        assertNull(optFamily2.get().getComment());

        boolean exists = familyRepository.existsById(ANDERSEN_FAMILY.getId());
        assertTrue(exists);

        exists = familyRepository.existsById(UUID.randomUUID().toString());
        assertFalse(exists);

        exists = familyRepository.existsByIdAndRegistered(ANDERSEN_FAMILY.getId(), true);
        assertFalse(exists);

        exists = familyRepository.existsByIdAndRegistered(ANDERSEN_FAMILY.getId(), false);
        assertTrue(exists);

        long count = familyRepository.count();
        assertTrue(count >= 2);

        count = familyRepository.countByRegistered(false);
        assertTrue(count >= 2);

        // Using raw query for update is not supported
        try {
            familyRepository.updateLastName(ANDERSEN_FAMILY.getId(), "New Last Name");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Cosmos Db does not support raw update queries"));
        }

        Address address = optFamily1.get().getAddress();
        address.setCity("LA");
        address.setState("CA");
        address.setCounty("Los Angeles");
        familyRepository.updateAddress(ANDERSEN_FAMILY.getId(), address);
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.getId());
        assertTrue(optFamily1.isPresent());
        assertEquals("Los Angeles", optFamily1.get().getAddress().getCounty());
        assertEquals("LA", optFamily1.get().getAddress().getCity());
        assertEquals("CA", optFamily1.get().getAddress().getState());

        familyRepository.updateRegistered(ANDERSEN_FAMILY.getId(), true);
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.getId());
        assertTrue(optFamily1.isPresent());
        assertTrue(optFamily1.get().isRegistered());

        familyRepository.updateRegistered(WAKEFIELD_FAMILY.getId(), true, new PartitionKey(optFamily2.get().getLastName()));
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.getId());
        assertTrue(optFamily2.isPresent());
        assertTrue(optFamily2.get().isRegistered());

        optFamily1.get().getAddress().setState("FL");
        familyRepository.update(optFamily1.get());
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.getId());
        assertTrue(optFamily1.isPresent());
        assertEquals("FL", optFamily1.get().getAddress().getState());

        Child newChild = new Child();
        newChild.setGrade(1);
        newChild.setGender("male");
        newChild.setFirstName("Isaac");
        optFamily2.get().getChildren().add(newChild);
        optFamily1.get().getAddress().setState("NY");
        familyRepository.updateAll(Arrays.asList(optFamily1.get(), optFamily2.get()));
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.getId());
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.getId());
        assertTrue(optFamily2.isPresent());
        assertEquals(optFamily2.get().getChildren().size(), WAKEFIELD_FAMILY.getChildren().size() + 1);
        assertTrue(optFamily1.isPresent());
        assertEquals("NY", optFamily1.get().getAddress().getState());

        // Using raw query for delete is not supported
        try {
            familyRepository.deleteByRegistered(false);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Cosmos Db does not support raw delete queries"));
        }

        familyRepository.deleteById(WAKEFIELD_FAMILY.getId(), new PartitionKey(optFamily2.get().getLastName()));
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.getId());
        assertFalse(optFamily2.isPresent());

        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.getId());
        assertTrue(optFamily1.isPresent());
        familyRepository.delete(optFamily1.get());
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.getId());
        assertFalse(optFamily1.isPresent());

        familyRepository.saveAll(Arrays.asList(createAndersenFamily(), createWakefieldFamily()));
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.getId());
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.getId());
        assertTrue(optFamily1.isPresent());
        assertTrue(optFamily2.isPresent());

        familyRepository.deleteAll();
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.getId());
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.getId());
        assertFalse(optFamily1.isPresent());
        assertFalse(optFamily2.isPresent());

        saveSampleFamilies();
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.getId());
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.getId());
        assertTrue(optFamily1.isPresent());
        assertTrue(optFamily2.isPresent());

        familyRepository.deleteAll(Arrays.asList(optFamily1.get(), optFamily2.get()));
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.getId());
        optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.getId());
        assertFalse(optFamily1.isPresent());
        assertFalse(optFamily2.isPresent());

        familyRepository.save(createAndersenFamily());
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.getId());
        assertTrue(optFamily1.isPresent());
        String lastName = optFamily1.get().getLastName();
        familyRepository.deleteByLastName(lastName, new PartitionKey(lastName));
        optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.getId());
        assertFalse(optFamily1.isPresent());
    }

    @Test
    void testCriteria() {
        saveSampleFamilies();
        assertEquals(2, familyRepository.findAll(childrenArrayContainsGender(new GenderAware("female"))).size());
        assertTrue(familyRepository.findOne(lastNameEquals("Andersen")).isPresent());
        assertFalse(familyRepository.findOne(lastNameEquals(UUID.randomUUID().toString())).isPresent());
        assertEquals(2, familyRepository.findAll(idsIn(ANDERSEN_FAMILY.getId(), WAKEFIELD_FAMILY.getId())).size());
        assertEquals(1, familyRepository.findAll(idsIn(ANDERSEN_FAMILY.getId())).size());
        assertEquals(2, familyRepository.findByIdIn(Arrays.asList(ANDERSEN_FAMILY.getId(), WAKEFIELD_FAMILY.getId())).size());
        assertEquals(1, familyRepository.findByIdIn(Collections.singletonList(ANDERSEN_FAMILY.getId())).size());
        assertEquals(1, familyRepository.findAll(idsNotIn(ANDERSEN_FAMILY.getId())).size());
        assertEquals(1, familyRepository.findByIdNotIn(Collections.singletonList(ANDERSEN_FAMILY.getId())).size());
        assertEquals(2, familyRepository.findAll(idsInAndNotIn(Arrays.asList(ANDERSEN_FAMILY.getId(), WAKEFIELD_FAMILY.getId()), Collections.singletonList(UUID.randomUUID().toString()))).size());
        assertTrue(CollectionUtils.isEmpty(familyRepository.findAll(idsInAndNotIn(Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString()), Arrays.asList(ANDERSEN_FAMILY.getId(), WAKEFIELD_FAMILY.getId())))));
        assertTrue(familyRepository.findAll(tagsContain("customtag")).stream().anyMatch(f -> f.getId().equals(ANDERSEN_FAMILY.getId())));
    }

    void saveSampleFamilies() {
        familyRepository.save(createAndersenFamily());
        familyRepository.save(createWakefieldFamily());
    }

    private static Family createAndersenFamily() {
        Family family = new Family();
        family.setId("AndersenFamily");
        family.setLastName("Andersen");
        Address address = new Address();
        address.setCity("Seattle");
        address.setCounty("King");
        address.setState("WA");
        family.setAddress(address);
        family.setTags(new String[]{"tag1", "customtag"});
        Child child1 = new Child();
        child1.setFirstName("Henriette Thaulow");
        child1.setGender("female");
        child1.setGrade(5);
        Pet pet1 = new Pet();
        pet1.setGivenName("Sadik");
        pet1.setType(PetType.CAT);
        child1.getPets().add(pet1);
        Pet pet2 = new Pet();
        pet2.setGivenName("Leo");
        pet2.setType(PetType.DOG);
        child1.getPets().add(pet2);
        family.getChildren().add(child1);
        return family;
    }

    private static Family createWakefieldFamily() {
        Family family = new Family();
        family.setId("WakefieldFamily");
        family.setLastName("Wakefield");
        Address address = new Address();
        address.setCity("NY");
        address.setCounty("Manhattan");
        address.setState("NY");
        family.setAddress(address);
        Child child1 = new Child();
        child1.setFirstName("Merriam");
        child1.setGender("female");
        child1.setGrade(6);
        family.getChildren().add(child1);
        Child child2 = new Child();
        child2.setFirstName("Luke");
        child2.setGender("male");
        child2.setGrade(8);
        Pet pet1 = new Pet();
        pet1.setGivenName("Robbie");
        pet1.setType(PetType.HAMSTER);
        child2.getPets().add(pet1);
        family.getChildren().add(child2);
        return family;
    }
}
