package example

import io.micronaut.data.mongo.annotation.MongoRepository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import org.bson.types.ObjectId
import java.util.*

// tag::repository[]
@MongoRepository
interface PersonRepository : CrudRepository<Person, ObjectId>, JpaSpecificationExecutor<Person> {
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

        fun nameEquals(name: String?) = PredicateSpecification<Person> { root, criteriaBuilder ->
            criteriaBuilder.equal(root.get<Any>("name"), name)
        }

        fun ageIsLessThan(age: Int) = PredicateSpecification<Person> { root, criteriaBuilder ->
            criteriaBuilder.lessThan(root.get("age"), age)
        }

        // end::specifications[]
        fun setNewName(newName: String) = UpdateSpecification<Person> { root, query, criteriaBuilder ->
            // tag::setUpdate[]
            query.set(root.get("name"), newName)
            // end::setUpdate[]
            null
        }

        // tag::specifications[]
    }
    // end::allSpecifications[]
    // end::specifications[]
    // tag::repository[]
}