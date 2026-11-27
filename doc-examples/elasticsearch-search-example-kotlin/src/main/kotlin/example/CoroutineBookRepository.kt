package example

import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.micronaut.data.repository.kotlin.CoroutineSearchRepository
import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository
import kotlinx.coroutines.flow.Flow

// tag::coroutine[]
@ElasticsearchRepository
interface CoroutineBookRepository : CoroutineCrudRepository<Book, String>, CoroutineSearchRepository<Book, String> { // <1>

    suspend fun searchBySearch(query: String): SearchResults<Book> // <2>

    fun findByTitleSearch(query: String): Flow<Book> // <3>
}
// end::coroutine[]
