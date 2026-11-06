package example

import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.data.repository.reactive.ReactorSearchRepository
import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

// tag::reactive[]
@ElasticsearchRepository
interface ReactiveBookRepository : ReactorCrudRepository<Book, String>, ReactorSearchRepository<Book, String> { // <1>

    fun searchBySearch(query: String): Mono<SearchResults<Book>> // <2>

    fun findByTitleSearch(query: String): Flux<Book> // <3>
}
// end::reactive[]
