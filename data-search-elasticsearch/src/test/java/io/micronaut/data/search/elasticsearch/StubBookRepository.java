package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import io.micronaut.data.search.tck.entities.Book;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.search.tck.repositories.ReactorBookSearchRepository;

/**
 * Backed by the in-memory stub transport of {@code StubTransportReactiveSpec}.
 */
@ElasticsearchRepository("stub")
public interface StubBookRepository extends ReactorBookSearchRepository {

    @Query("{\"query\": {\"match\": {\"title\": \":title\"}}, \"size\": 5}")
    Flux<Book> rawTitle(String title);

    @Query("{\"query\": {\"bool\": {\"must\": {\"simple_query_string\": {\"query\": \":query\", \"fields\": [\"title\"]}}, \"filter\": {\"range\": {\"pages\": {\"gte\": \":minPages\"}}}}}}")
    Mono<SearchResults<Book>> rawSearch(String query, int minPages);
}
