package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;

/**
 * A reactive repository over the versioned entity, run through the stub async transport.
 */
@ElasticsearchRepository("stub")
public interface StubReactiveVersionedBookRepository extends ReactorCrudRepository<VersionedBook, String> {
}
