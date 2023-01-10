package io.micronaut.data.hibernate6.async;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate6.entities.UserWithWhere;
import io.micronaut.data.repository.async.AsyncCrudRepository;

import java.util.UUID;

@Repository
public interface AsyncUserWithWhereRepository extends AsyncCrudRepository<UserWithWhere, UUID> {
}
