package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;
import io.micronaut.data.search.tck.repositories.AsyncBookSearchRepository;

@ElasticsearchRepository
public interface ElasticsearchAsyncBookSearchRepository extends AsyncBookSearchRepository {
}
