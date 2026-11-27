package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;
import io.micronaut.data.search.tck.repositories.AsyncBookSearchRepository;

/**
 * Backed by the in-memory stub transport of {@code StubTransportReactiveSpec}.
 */
@ElasticsearchRepository("stub")
public interface StubAsyncBookRepository extends AsyncBookSearchRepository {
}
