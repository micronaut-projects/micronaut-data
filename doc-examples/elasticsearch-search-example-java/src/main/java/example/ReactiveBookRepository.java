package example;

import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import io.micronaut.data.repository.reactive.ReactorSearchRepository;
import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// tag::reactive[]
@ElasticsearchRepository
public interface ReactiveBookRepository extends ReactorCrudRepository<Book, String>, ReactorSearchRepository<Book, String> { // <1>

    Mono<SearchResults<Book>> searchBySearch(String query); // <2>

    Flux<Book> findByTitleSearch(String query); // <3>
}
// end::reactive[]
