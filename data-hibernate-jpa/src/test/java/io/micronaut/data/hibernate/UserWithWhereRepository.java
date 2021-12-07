package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.entities.UserWithWhere;
import io.micronaut.data.repository.CrudRepository;

import java.util.UUID;

@Repository
public interface UserWithWhereRepository extends CrudRepository<UserWithWhere, UUID> {
}
