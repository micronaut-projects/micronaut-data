package example

import io.micronaut.data.cosmos.annotation.CosmosRepository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.data.runtime.criteria.get
import io.micronaut.data.runtime.criteria.query
import io.micronaut.data.runtime.criteria.update
import io.micronaut.data.runtime.criteria.where
import java.util.*

// tag::repository[]
@CosmosRepository
interface PersonRepository : CrudRepository<Person, String>, JpaSpecificationExecutor<Person> {
    // end::repository[]
    override
    // tag::find[]
    fun findOne(spec: PredicateSpecification<Person>?): Optional<Person>

    // end::find[]
    override
    // tag::find[]
    fun findOne(spec: QuerySpecification<Person>?): Optional<Person>

    // end::find[]
    override
    // tag::find[]
    fun findAll(spec: PredicateSpecification<Person>?): List<Person>

    // end::find[]
    override
    // tag::find[]
    fun findAll(spec: QuerySpecification<Person>?): List<Person>

    // end::find[]
    override
    // tag::find[]
    fun findAll(spec: PredicateSpecification<Person>?, sort: Sort): List<Person>

    // end::find[]
    override
    // tag::find[]
    fun findAll(spec: QuerySpecification<Person>?, sort: Sort): List<Person>

    // end::find[]
    override
    // tag::find[]
    fun findAll(spec: PredicateSpecification<Person>?, pageable: Pageable): Page<Person>

    // end::find[]
    override
    // tag::find[]
    fun findAll(spec: QuerySpecification<Person>?, pageable: Pageable): Page<Person>

    // end::find[]
    override
    // tag::count[]
    fun count(spec: PredicateSpecification<Person>?): Long

    // end::count[]
    override
    // tag::count[]
    fun count(spec: QuerySpecification<Person>?): Long

    // end::count[]
    override
    // tag::update[]
    fun updateAll(spec: UpdateSpecification<Person>?): Long

    // end::update[]
    override
    // tag::delete[]
    fun deleteAll(spec: PredicateSpecification<Person>?): Long

    // end::delete[]
    override
    // tag::delete[]
    fun deleteAll(spec: DeleteSpecification<Person>?): Long

    // end::delete[]
    // tag::specifications[]
    // tag::allSpecifications[]
    object Specifications {
        // tag::where[]
        fun nameEquals(name: String?) = where<Person> { root[Person::name] eq name }

        fun ageIsLessThan(age: Int) = where<Person> { root[Person::age] lt age }

        fun nameInList(names: List<String>) = where<Person> { root[Person::name] inList names }
        // end::where[]

        // tag::or[]
        fun nameOrAgeMatches(age: Int, name: String?) = where<Person> {
            or {
                root[Person::name] eq name
                root[Person::age] lt age
            }
        }

        fun nameAndAgeMatch(age: Int, name: String) = query<Person> {
            where {
                root[Person::name] eq name
                root[Person::age] lt age
            }
        }
        // end::or[]

        // end::specifications[]
        // tag::setUpdate[]
        fun updateName(newName: String, existingName: String) = update<Person> {
            set(Person::name, newName)
            where {
                root[Person::name] eq existingName
            }
        }
        // end::setUpdate[]

        // Different style using the criteria builder
        fun nameEquals2(name: String?) = PredicateSpecification { root, criteriaBuilder ->
            criteriaBuilder.equal(root[Person::name], name)
        }

        fun ageIsLessThan2(age: Int) = PredicateSpecification { root, criteriaBuilder ->
            criteriaBuilder.lessThan(root[Person::age], age)
        }

        fun setNewName2(newName: String) = UpdateSpecification { root, query, _ ->
            // tag::setUpdate[]
            query.set(root[Person::name], newName)
            // end::setUpdate[]
            null
        }

        // tag::specifications[]
    }
    // end::allSpecifications[]
    // end::specifications[]
    // tag::repository[]
}

