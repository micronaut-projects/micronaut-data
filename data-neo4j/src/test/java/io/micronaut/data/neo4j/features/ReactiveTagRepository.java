package io.micronaut.data.neo4j.features;

import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Mono;

@Neo4jRepository
public interface ReactiveTagRepository extends ReactorCrudRepository<Tag, Long> {
    Mono<CursoredPage<Tag>> findByNameStartsWith(String prefix, Pageable pageable);
}
