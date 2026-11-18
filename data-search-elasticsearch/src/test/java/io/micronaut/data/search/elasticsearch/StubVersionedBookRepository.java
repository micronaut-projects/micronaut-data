package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;
import io.micronaut.data.repository.CrudRepository;

/**
 * Backed by the in-memory stub transport of {@code StubTransportReactiveSpec}.
 */
@ElasticsearchRepository("stub")
public interface StubVersionedBookRepository extends CrudRepository<VersionedBook, String> {
}
