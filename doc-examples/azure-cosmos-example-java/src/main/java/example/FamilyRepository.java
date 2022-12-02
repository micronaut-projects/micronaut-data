package example;

import com.azure.cosmos.models.PartitionKey;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.cosmos.annotation.CosmosRepository;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.repository.PageableRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@CosmosRepository
public abstract class FamilyRepository implements PageableRepository<Family, String>, JpaSpecificationExecutor<Family> {

    @NonNull
    public abstract Optional<Family> findById(String id);

    public abstract void updateRegistered(@Id String id, boolean registered);

    public abstract void updateAddress(@Parameter("id") @Id String id, @NonNull @Parameter("address") Address address);

    // Raw query for Cosmos update is not supported and calling this method will throw an error.
    @Query("UPDATE family f SET f.lastName=@p1 WHERE f.id=@p2")
    public abstract void updateLastName(String id, String lastName);

    // tag::partitionkey[]
    public abstract Optional<Family> queryById(String id, PartitionKey partitionKey);

    public abstract void updateRegistered(@Id String id, boolean registered, PartitionKey partitionKey);

    public abstract void deleteByLastName(String lastName, PartitionKey partitionKey);

    public abstract void deleteById(String id, PartitionKey partitionKey);
    // end::partitionkey[]

    // Raw query not supported for delete so this would throw an error
    @Query("DELETE FROM family f WHERE f.registered=@p1")
    public abstract void deleteByRegistered(boolean registered);

    public abstract boolean existsByIdAndRegistered(String id, boolean registered);

    public abstract long countByRegistered(boolean registered);

    @Query("SELECT VALUE f.lastName FROM family f ORDER BY f.lastName DESC OFFSET 0 LIMIT 1")
    public abstract String lastOrderedLastName();

    @Query("SELECT VALUE f.registeredDate FROM family f WHERE NOT IS_NULL(f.registeredDate) ORDER BY f.registeredDate DESC OFFSET 0 LIMIT 1")
    public abstract Date lastOrderedRegisteredDate();

    // tag::relations[]
    public abstract List<Family> findByAddressStateAndAddressCityOrderByAddressCity(String state, String city);

    public abstract void updateByAddressCounty(String county, boolean registered, @Nullable Date registeredDate);

    @Join(value = "children.pets", alias = "pets")
    public abstract List<Family> findByChildrenPetsType(PetType type);

    public abstract List<Child> findChildrenByChildrenPetsGivenName(String name);
    // end::relations[]

    public abstract List<Family> findByIdIn(List<String> ids);

    public abstract List<Family> findByIdNotIn(List<String> ids);

    public abstract List<Family> findByLastNameLike(String lastName);

    // tag::method_array_contains[]
    public abstract List<Family> findByTagsArrayContains(String tag);
    // end::method_array_contains[]

    // tag::array_contains_partial[]
    @Query("SELECT DISTINCT VALUE f FROM family f WHERE ARRAY_CONTAINS(f.children, :gender, true)")
    public abstract List<Family> childrenArrayContainsGender(Map.Entry<String, Object> gender);
    // end::array_contains_partial[]

    static class Specifications {

        public static PredicateSpecification<Family> lastNameEquals(String lastName) {
            return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get("lastName"), lastName);
        }

        public static PredicateSpecification<Family> idsIn(String... ids) {
            return (root, criteriaBuilder) -> root.get("id").in(Arrays.asList(ids));
        }

        public static PredicateSpecification<Family> idsNotIn(String... ids) {
            return (root, criteriaBuilder) -> root.get("id").in(Arrays.asList(ids)).not();
        }

        public static PredicateSpecification<Family> idsInAndNotIn(List<String> idsIn, List<String> idsNotIn) {
            return (root, criteriaBuilder) -> criteriaBuilder.and(root.get("id").in(idsIn), root.get("id").in(idsNotIn).not());
        }

        // tag::predicate_array_contains[]
        public static PredicateSpecification<Family> tagsContain(String tag) {
            return (root, criteriaBuilder) -> ((PersistentEntityCriteriaBuilder) criteriaBuilder).arrayContains(root.get("tags"), criteriaBuilder.literal(tag));
        }
        // end::predicate_array_contains[]

        // tag::predicate_array_contains_partial[]
        public static PredicateSpecification<Family> childrenArrayContainsGender(GenderAware gender) {
            return (root, criteriaBuilder) -> ((PersistentEntityCriteriaBuilder) criteriaBuilder).arrayContains(root.join("children"), criteriaBuilder.literal(gender));
        }
        // end::predicate_array_contains_partial[]
    }
}
