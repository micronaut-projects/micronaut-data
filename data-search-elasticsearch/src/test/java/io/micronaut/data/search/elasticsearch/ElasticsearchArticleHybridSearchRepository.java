package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository;

@ElasticsearchRepository
public interface ElasticsearchArticleHybridSearchRepository extends ArticleHybridSearchRepository {
}
