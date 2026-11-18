package example

import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.SearchRepository

// tag::repository[]
@ElasticsearchRepository
interface BookRepository : CrudRepository<Book, String>, SearchRepository<Book, String> { // <1>

    fun findByTitleSearch(query: String): List<Book> // <2>

    fun searchByTitleSearchAll(query: String): SearchResults<Book> // <3>

    fun findByDescriptionSearchPhrase(query: String): List<Book> // <4>

    fun findByTitleSearchPrefix(query: String): List<Book> // <5>

    fun findBySearchAndAuthor(query: String, author: String): List<Book> // <6>
}
// end::repository[]
