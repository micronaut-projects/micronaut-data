package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;
import io.micronaut.data.search.tck.repositories.ReactorBookSearchRepository;

/**
 * Backed by the in-memory stub transport of {@code StubTransportReactiveSpec}.
 */
@ElasticsearchRepository("stub")
public interface StubBookRepository extends ReactorBookSearchRepository {
}
