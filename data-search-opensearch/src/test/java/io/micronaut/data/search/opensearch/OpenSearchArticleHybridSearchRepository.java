package io.micronaut.data.search.opensearch;

import io.micronaut.data.search.opensearch.annotation.OpenSearchRepository;
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository;

@OpenSearchRepository
public interface OpenSearchArticleHybridSearchRepository extends ArticleHybridSearchRepository {
}
