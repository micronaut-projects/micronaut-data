package io.micronaut.data.hibernate.reactive.reactive;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.reactive.entities.UserWithWhere;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;

import java.util.UUID;

@Repository
public interface ReactorUserWithWhereRepository extends ReactorCrudRepository<UserWithWhere, UUID> {
}
