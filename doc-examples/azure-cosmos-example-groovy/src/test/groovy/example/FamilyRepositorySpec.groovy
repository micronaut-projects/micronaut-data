package example

import com.azure.cosmos.models.PartitionKey
import io.micronaut.context.BeanContext
import io.micronaut.core.util.StringUtils
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.IgnoreIf
import spock.lang.Shared

import static example.FamilyRepository.Specifications.childrenArrayContainsGender
import static example.FamilyRepository.Specifications.idsIn
import static example.FamilyRepository.Specifications.idsInAndNotIn
import static example.FamilyRepository.Specifications.idsNotIn
import static example.FamilyRepository.Specifications.lastNameEquals
import static example.FamilyRepository.Specifications.tagsContain


@MicronautTest
@IgnoreIf({ env["GITHUB_WORKFLOW"] })
class FamilyRepositorySpec extends AbstractAzureCosmosSpec {

    private static final Family ANDERSEN_FAMILY = createAndersenFamily()
    private static final Family WAKEFIELD_FAMILY = createWakefieldFamily()

    // tag::inject[]
    @Inject @Shared FamilyRepository familyRepository
    // end::inject[]

    // tag::metadata[]
    @Inject @Shared BeanContext beanContext

    void cleanup() {
        familyRepository.deleteAll()
    }

    def "test Crud"() {
        given:
            saveSampleFamilies()
        when:
            def families = familyRepository.childrenArrayContainsGender(new AbstractMap.SimpleImmutableEntry<String, Object>("gender", "male"))
        then:
            families.size() == 1
        when:
            def optFamily1 = familyRepository.findById(ANDERSEN_FAMILY.id)
            def optFamily2 = familyRepository.findById(WAKEFIELD_FAMILY.id)
        then:
            optFamily1.present
            optFamily1.get().id == ANDERSEN_FAMILY.id
            optFamily1.get().children.size() == ANDERSEN_FAMILY.children.size()
            optFamily1.get().address
            optFamily1.get().documentVersion
            optFamily2.present
            optFamily2.get().id == WAKEFIELD_FAMILY.id
            optFamily2.get().children.size() == WAKEFIELD_FAMILY.children.size()
            optFamily2.get().address
            optFamily2.get().documentVersion
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
            families = familyRepository.findByTagsArrayContains("customtag")
        then:
            families.size() == 1
            families[0].id == ANDERSEN_FAMILY.id
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
            def cnt = familyRepository.count()
        then:
            cnt >= 2
        when:
            cnt = familyRepository.countByRegistered(false)
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
    }

    void "test criteria" () {
        when:
            saveSampleFamilies()
            def genderAware = new GenderAware()
            genderAware.gender = "female"
        then:
            familyRepository.findAll(childrenArrayContainsGender(genderAware)).size() == 2
            familyRepository.findOne(lastNameEquals("Andersen")).present
            !familyRepository.findOne(lastNameEquals(UUID.randomUUID().toString())).present
            familyRepository.findAll(idsIn(ANDERSEN_FAMILY.id, WAKEFIELD_FAMILY.id)).size() == 2
            familyRepository.findAll(idsIn(ANDERSEN_FAMILY.id)).size() == 1
            familyRepository.findByIdIn(Arrays.asList(ANDERSEN_FAMILY.id, WAKEFIELD_FAMILY.id)).size() == 2
            familyRepository.findByIdIn(Arrays.asList(ANDERSEN_FAMILY.id)).size() == 1
            familyRepository.findAll(idsNotIn(ANDERSEN_FAMILY.id)).size() == 1
            familyRepository.findByIdNotIn(Arrays.asList(ANDERSEN_FAMILY.id)).size() == 1
            familyRepository.findAll(idsInAndNotIn(Arrays.asList(ANDERSEN_FAMILY.id, WAKEFIELD_FAMILY.id), Arrays.asList(UUID.randomUUID().toString()))).size() == 2
            familyRepository.findAll(idsInAndNotIn(Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString()), Arrays.asList(ANDERSEN_FAMILY.id, WAKEFIELD_FAMILY.id))).size() == 0
            familyRepository.findAll(tagsContain("customtag")).stream().filter { f -> f.id == ANDERSEN_FAMILY.id }
                .findFirst().present
    }

    static Family createAndersenFamily() {
        def family = new Family()
        family.id = "AndersenFamily"
        family.lastName = "Andersen"
        def address = new Address()
        address.city = "Seattle"
        address.county = "King"
        address.state = "WA"
        family.address = address
        family.tags = ["tag1", "customtag"]
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
}
