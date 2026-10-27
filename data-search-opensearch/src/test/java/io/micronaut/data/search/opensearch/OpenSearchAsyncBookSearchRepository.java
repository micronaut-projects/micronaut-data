package io.micronaut.data.search.opensearch;

import io.micronaut.data.search.opensearch.annotation.OpenSearchRepository;
import io.micronaut.data.search.tck.repositories.AsyncBookSearchRepository;

@OpenSearchRepository
public interface OpenSearchAsyncBookSearchRepository extends AsyncBookSearchRepository {
}
