package io.micronaut.data.document.mongodb.search;

import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.async.AsyncCrudRepository;
import io.micronaut.data.repository.async.AsyncSearchRepository;

import java.util.UUID;

@MongoRepository
public interface MongoAtlasAsyncBookSearchRepository extends AsyncCrudRepository<AtlasBook, UUID>, AsyncSearchRepository<AtlasBook, UUID> {
}
