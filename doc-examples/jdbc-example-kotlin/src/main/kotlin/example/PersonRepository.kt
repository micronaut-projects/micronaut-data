package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.data.repository.jpa.kotlin.KotlinJpaSpecificationExecutor
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

// tag::repository[]
@JdbcRepository(dialect = Dialect.H2)
interface PersonRepository : KotlinCrudRepository<Person, Long>, KotlinJpaSpecificationExecutor<Person> {
    // end::repository[]
    override
    // tag::find[]
    fun findOne(spec: PredicateSpecification<Person>?): Person?

    // end::find[]
    override
    // tag::find[]
    fun findOne(spec: QuerySpecification<Person>?): Person?

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
        fun setNewName(newName: String) = UpdateSpecification<Person> { root, query, _ ->
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
