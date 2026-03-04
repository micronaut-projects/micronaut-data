package example

import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import java.util.Optional

// tag::repository[]
@NitriteRepository
interface PersonRepository : CrudRepository<Person, String>, JpaSpecificationExecutor<Person> {
// end::repository[]

    // tag::find[]
    override fun findOne(spec: PredicateSpecification<Person>): Optional<Person>

    override fun findOne(spec: QuerySpecification<Person>): Optional<Person>

    override fun findAll(spec: PredicateSpecification<Person>): List<Person>

    override fun findAll(spec: QuerySpecification<Person>): List<Person>

    override fun findAll(spec: PredicateSpecification<Person>, sort: Sort): List<Person>

    override fun findAll(spec: QuerySpecification<Person>, sort: Sort): List<Person>

    override fun findAll(spec: PredicateSpecification<Person>, pageable: Pageable): Page<Person>

    override fun findAll(spec: QuerySpecification<Person>, pageable: Pageable): Page<Person>
    // end::find[]

    // tag::count[]
    override fun count(spec: PredicateSpecification<Person>): Long

    override fun count(spec: QuerySpecification<Person>): Long
    // end::count[]

    // tag::update[]
    override fun updateAll(spec: UpdateSpecification<Person>): Long
    // end::update[]

    // tag::delete[]
    override fun deleteAll(spec: PredicateSpecification<Person>): Long

    override fun deleteAll(spec: DeleteSpecification<Person>): Long
    // end::delete[]

    // tag::specifications[]
    // tag::allSpecifications[]
    class Specifications {
        companion object {
            fun nameEquals(name: String): PredicateSpecification<Person> =
                PredicateSpecification { root, criteriaBuilder -> criteriaBuilder.equal(root.get<Any>("name"), name) }

            fun ageIsLessThan(age: Int): PredicateSpecification<Person> =
                PredicateSpecification { root, criteriaBuilder -> criteriaBuilder.lessThan(root.get("age"), age) }

            fun setNewName(newName: String): UpdateSpecification<Person> =
                UpdateSpecification { root, query, _ ->
                    // tag::setUpdate[]
                    query.set(root.get<Any>("name"), newName)
                    // end::setUpdate[]
                    null
                }

            fun interestsContains(interest: String): PredicateSpecification<Person> =
                PredicateSpecification { root, criteriaBuilder ->
                    (criteriaBuilder as PersistentEntityCriteriaBuilder)
                        .arrayContains(root.get("interests"), criteriaBuilder.literal(interest))
                }
        }
    }
    // end::allSpecifications[]
    // end::specifications[]

// tag::repository[]
}
// end::repository[]

