package io.micronaut.data.search.opensearch;

import io.micronaut.data.search.opensearch.annotation.OpenSearchRepository;
import io.micronaut.data.search.tck.repositories.ReactorBookSearchRepository;

@OpenSearchRepository
public interface OpenSearchReactorBookSearchRepository extends ReactorBookSearchRepository {
}
