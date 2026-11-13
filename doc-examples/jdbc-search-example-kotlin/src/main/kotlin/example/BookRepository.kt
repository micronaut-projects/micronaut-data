package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.SearchRepository

// tag::repository[]
@JdbcRepository(dialect = Dialect.POSTGRES)
interface BookRepository : CrudRepository<Book, Long>, SearchRepository<Book, Long> { // <1>

    fun findByTitleSearch(query: String): List<Book> // <2>

    fun searchByTitleSearchAll(query: String): SearchResults<Book> // <3>

    fun findByDescriptionSearchPhrase(query: String): List<Book> // <4>

    fun findByTitleSearchPrefix(query: String): List<Book> // <5>

    fun findBySearchAndAuthor(query: String, author: String): List<Book> // <6>

    fun searchByTitleSearch(query: String, minScore: Score): SearchResults<Book> // <7>
}
// end::repository[]
