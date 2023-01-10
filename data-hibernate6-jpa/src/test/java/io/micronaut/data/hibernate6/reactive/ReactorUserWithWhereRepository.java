package io.micronaut.data.hibernate6.reactive;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate6.entities.UserWithWhere;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;

import java.util.UUID;

@Repository
public interface ReactorUserWithWhereRepository extends ReactorCrudRepository<UserWithWhere, UUID> {
}
