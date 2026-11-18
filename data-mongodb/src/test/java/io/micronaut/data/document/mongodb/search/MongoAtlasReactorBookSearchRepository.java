package io.micronaut.data.document.mongodb.search;

import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import io.micronaut.data.repository.reactive.ReactorSearchRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@MongoRepository
public interface MongoAtlasReactorBookSearchRepository extends ReactorCrudRepository<AtlasBook, UUID>, ReactorSearchRepository<AtlasBook, UUID> {

    Mono<SearchResults<AtlasBook>> searchBySearch(String query);

    Flux<AtlasBook> findByTitleSearch(String query);

    Mono<Long> countBySearch(String query);
}
