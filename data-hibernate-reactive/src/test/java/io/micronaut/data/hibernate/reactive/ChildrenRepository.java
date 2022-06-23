package io.micronaut.data.hibernate.reactive;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.reactive.entities.Children;
import io.micronaut.data.hibernate.reactive.entities.ChildrenId;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Mono;

@Repository
public interface ChildrenRepository extends ReactorCrudRepository<Children, ChildrenId> {

    @Query(nativeQuery = true, value = "SELECT max(c.number) FROM children c WHERE c.parent_id = :parentId")
    Mono<Integer> getMaxNumber(int parentId);
}
