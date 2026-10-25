package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository;

/**
 * Backed by the in-memory stub transport of {@code StubTransportReactiveSpec}.
 */
@ElasticsearchRepository("stub")
public interface StubArticleRepository extends ArticleHybridSearchRepository {

    io.micronaut.data.model.vector.search.SearchResults<io.micronaut.data.search.tck.entities.Article> searchByEmbeddingNear(io.micronaut.data.model.vector.Vector vector,
                                                                                                                   io.micronaut.data.model.vector.search.Score maxDistance,
                                                                                                                   io.micronaut.data.model.Pageable pageable);
}
