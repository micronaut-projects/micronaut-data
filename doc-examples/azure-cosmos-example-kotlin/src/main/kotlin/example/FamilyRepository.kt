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
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Root
import java.util.*

@CosmosRepository
abstract class FamilyRepository : PageableRepository<Family, String>, JpaSpecificationExecutor<Family> {
    @NonNull
    abstract override fun findById(id: String): Optional<Family>
    abstract fun updateRegistered(@Id id: String, registered: Boolean)
    abstract fun updateAddress(@Parameter("id") @Id id: String, @NonNull @Parameter("address") address: Address)

    // Raw query for Cosmos update is not supported and calling this method will throw an error.
    @Query("UPDATE family f SET f.lastName=@p1 WHERE f.id=@p2")
    abstract fun updateLastName(id: String, lastName: String)

    // tag::partitionkey[]
    abstract fun queryById(id: String?, partitionKey: PartitionKey?): Optional<Family?>?
    abstract fun deleteByLastName(lastName: String, partitionKey: PartitionKey)
    abstract fun deleteById(id: String, partitionKey: PartitionKey)
    abstract fun updateRegistered(@Id id: String, registered: Boolean, partitionKey: PartitionKey)
    // end::partitionkey[]

    // Raw query not supported for delete so this would throw an error
    @Query("DELETE FROM family f WHERE f.registered=@p1")
    abstract fun deleteByRegistered(registered: Boolean)
    abstract fun existsByIdAndRegistered(id: String, registered: Boolean): Boolean
    abstract fun countByRegistered(registered: Boolean): Long
    @Query("SELECT VALUE f.lastName FROM family f ORDER BY f.lastName DESC OFFSET 0 LIMIT 1")
    abstract fun lastOrderedLastName(): String?
    @Query("SELECT VALUE f.registeredDate FROM family f WHERE NOT IS_NULL(f.registeredDate) ORDER BY f.registeredDate DESC OFFSET 0 LIMIT 1")
    abstract fun lastOrderedRegisteredDate(): Date?
    // tag::relations[]
    abstract fun findByAddressStateAndAddressCityOrderByAddressCity(state: String, city: String): List<Family>
    abstract fun updateByAddressCounty(county: String, registered: Boolean, @Nullable registeredDate: Date?)
    @Join(value = "children.pets", alias = "pets")
    abstract fun findByChildrenPetsType(type: PetType): List<Family>
    abstract fun findChildrenByChildrenPetsGivenName(name: String): List<Child>
    // end::relations[]
    abstract fun findByIdIn(ids: List<String>): List<Family>
    abstract fun findByIdNotIn(ids: List<String>): List<Family>
    abstract fun findByLastNameLike(lastName: String): List<Family>

    // tag::method_array_contains[]
    abstract fun findByTagsArrayContains(tag: String): List<Family>
    // end::method_array_contains[]

    // tag::array_contains_partial[]
    @Query("SELECT DISTINCT VALUE f FROM family f WHERE ARRAY_CONTAINS(f.children, :gender, true)")
    abstract fun childrenArrayContainsGender(gender: Map.Entry<String, Any>): List<Family>
    // end::array_contains_partial[]

    internal object Specifications {
        fun lastNameEquals(lastName: String): PredicateSpecification<Family> {
            return PredicateSpecification { root: Root<Family>, criteriaBuilder: CriteriaBuilder ->
                criteriaBuilder.equal(
                    root.get<Any>("lastName"),
                    lastName
                )
            }
        }

        fun idsIn(vararg ids: String): PredicateSpecification<Family> {
            return PredicateSpecification { root: Root<Family>, _: CriteriaBuilder ->
                root.get<Any>("id").`in`(
                    listOf(*ids)
                )
            }
        }

        fun idsNotIn(vararg ids: String): PredicateSpecification<Family> {
            return PredicateSpecification { root: Root<Family>, _: CriteriaBuilder ->
                root.get<Any>("id").`in`(
                    listOf(*ids)
                ).not()
            }
        }

        fun idsInAndNotIn(idsIn: List<String>, idsNotIn: List<String>): PredicateSpecification<Family> {
            return PredicateSpecification { root: Root<Family>, criteriaBuilder: CriteriaBuilder ->
                criteriaBuilder.and(
                    root.get<Any>("id").`in`(idsIn),
                    root.get<Any>("id").`in`(idsNotIn).not()
                )
            }
        }

        // tag::predicate_array_contains[]
        fun tagsContain(tag: String): PredicateSpecification<Family?>? {
            return PredicateSpecification { root: Root<Family?>, criteriaBuilder: CriteriaBuilder ->
                (criteriaBuilder as PersistentEntityCriteriaBuilder).arrayContains(
                    root.get<Any>("tags"),
                    criteriaBuilder.literal(tag)
                )
            }
        }
        // end::predicate_array_contains[]

        // tag::predicate_array_contains_partial[]
        fun childrenArrayContainsGender(gender: IGenderAware): PredicateSpecification<Family?>? {
            return PredicateSpecification { root: Root<Family?>, criteriaBuilder: CriteriaBuilder ->
                (criteriaBuilder as PersistentEntityCriteriaBuilder).arrayContains(
                    root.join<Any, Any>("children"),
                    criteriaBuilder.literal(gender)
                )
            }
        }
        // end::predicate_array_contains_partial[]
    }
}
