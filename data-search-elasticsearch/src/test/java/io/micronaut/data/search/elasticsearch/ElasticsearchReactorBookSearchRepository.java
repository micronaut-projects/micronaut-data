package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;
import io.micronaut.data.search.tck.repositories.ReactorBookSearchRepository;

@ElasticsearchRepository
public interface ElasticsearchReactorBookSearchRepository extends ReactorBookSearchRepository {
}
