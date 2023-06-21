package example

import com.azure.cosmos.models.PartitionKey
import io.micronaut.context.annotation.Parameter
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.cosmos.annotation.CosmosRepository
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder
import io.micronaut.data.repository.PageableRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification

@CosmosRepository
abstract class FamilyRepository implements PageableRepository<Family, String>, JpaSpecificationExecutor<Family> {

    @NonNull
    abstract Optional<Family> findById(String id)

    abstract void updateRegistered(@Id String id, boolean registered)

    abstract void updateAddress(@Parameter("id") @Id String id, @NonNull @Parameter("address") Address address)

    // Raw query for Cosmos update is not supported and calling this method will throw an error.
    @Query("UPDATE family f SET f.lastName=@p1 WHERE f.id=@p2")
    abstract void updateLastName(String id, String lastName)

    // tag::partitionkey[]
    abstract Optional<Family> queryById(String id, PartitionKey partitionKey)

    abstract void updateRegistered(@Id String id, boolean registered, PartitionKey partitionKey)

    abstract void deleteByLastName(String lastName, PartitionKey partitionKey)

    abstract void deleteById(String id, PartitionKey partitionKey)
    // end::partitionkey[]

    // Raw query not supported for delete so this would throw an error
    @Query("DELETE FROM family f WHERE f.registered=@p1")
    abstract void deleteByRegistered(boolean registered)

    abstract boolean existsByIdAndRegistered(String id, boolean registered)

    abstract long countByRegistered(boolean registered)

    @Query("SELECT VALUE f.lastName FROM family f ORDER BY f.lastName DESC OFFSET 0 LIMIT 1")
    abstract String lastOrderedLastName()

    @Query("SELECT VALUE f.registeredDate FROM family f WHERE NOT IS_NULL(f.registeredDate) ORDER BY f.registeredDate DESC OFFSET 0 LIMIT 1")
    abstract Date lastOrderedRegisteredDate()

    // tag::relations[]
    abstract List<Family> findByAddressStateAndAddressCityOrderByAddressCity(String state, String city)

    abstract void updateByAddressCounty(String county, boolean registered, @Nullable Date registeredDate)

    @Join(value = "children.pets", alias = "pets")
    abstract List<Family> findByChildrenPetsType(PetType type)

    abstract List<Child> findChildrenByChildrenPetsGivenName(String name)
    // end::relations[]

    abstract List<Family> findByIdIn(List<String> ids)

    abstract List<Family> findByIdNotIn(List<String> ids)

    abstract List<Family> findByLastNameLike(String lastName)

    // tag::method_array_contains[]
    abstract List<Family> findByTagsArrayContains(String tag)
    // end::method_array_contains[]

    // tag::array_contains_partial[]
    @Query("SELECT DISTINCT VALUE f FROM family f WHERE ARRAY_CONTAINS(f.children, :gender, true)")
    abstract List<Family> childrenArrayContainsGender(Map.Entry<String, Object> gender)
    // end::array_contains_partial[]

    static class Specifications {

        static PredicateSpecification<Family> lastNameEquals(String lastName) {
            return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get("lastName"), lastName)
        }

        static PredicateSpecification<Family> idsIn(String... ids) {
            return (root, criteriaBuilder) -> root.get("id").in(Arrays.asList(ids))
        }

        static PredicateSpecification<Family> idsNotIn(String... ids) {
            return (root, criteriaBuilder) -> root.get("id").in(Arrays.asList(ids)).not()
        }

        static PredicateSpecification<Family> idsInAndNotIn(List<String> idsIn, List<String> idsNotIn) {
            return (root, criteriaBuilder) -> criteriaBuilder.and(root.get("id").in(idsIn), root.get("id").in(idsNotIn).not())
        }

        // tag::predicate_array_contains[]
        static PredicateSpecification<Family> tagsContain(String tag) {
            return (root, criteriaBuilder) -> ((PersistentEntityCriteriaBuilder)criteriaBuilder).arrayContains(root.get("tags"), criteriaBuilder.literal(tag))
        }
        // end::predicate_array_contains[]

        // tag::predicate_array_contains_partial[]
        static PredicateSpecification<Family> childrenArrayContainsGender(GenderAware gender) {
            return (root, criteriaBuilder) -> ((PersistentEntityCriteriaBuilder) criteriaBuilder).arrayContains(root.join("children"), criteriaBuilder.literal(gender))
        }
        // end::predicate_array_contains_partial[]
    }

}
