package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import java.util.Optional

// tag::repository[]
@NitriteRepository
interface BookRepository : CrudRepository<Book, String>, JpaSpecificationExecutor<Book> {
// end::repository[]

    fun findByTitle(title: String): Optional<Book>

    // tag::join-students[]
    // Eagerly fetch students for all books (MANY_TO_MANY)
    @Join("students")
    override fun findAll(): List<Book>
    // end::join-students[]

    // tag::update[]
    fun update(@Id id: String, title: String)
    // end::update[]

    // tag::findByAuthorName[]
    fun findByAuthorName(authorName: String): List<Book>
    // end::findByAuthorName[]

    // tag::case-insensitive[]
    // Case-insensitive search by title
    fun findByTitleIgnoreCase(title: String): List<Book>

    // Case-insensitive contains
    fun findByTitleContainsIgnoreCase(keyword: String): List<Book>
    // end::case-insensitive[]

    // tag::in-queries[]
    // IN query with List parameter
    fun findByTitleIn(titles: List<String>): List<Book>

    // IN query with array parameter
    fun findByTitleIn(titles: Array<String>): List<Book>

    // NOT IN query
    fun findByTitleNotIn(titles: List<String>): List<Book>
    // end::in-queries[]

    // tag::projections[]
    // Count query - returns long
    fun countByAuthorId(authorId: String): Long

    // Aggregate functions
    fun countByTitle(title: String): Long?
    // end::projections[]

    companion object Specifications {
        // tag::association-navigation[]
        fun authorNameEquals(name: String): PredicateSpecification<Book> =
            PredicateSpecification { root, criteriaBuilder ->
                criteriaBuilder.equal(root.get<Any>("author").get<Any>("name"), name)
            }
        // end::association-navigation[]
    }

// tag::repository[]
}
// end::repository[]
