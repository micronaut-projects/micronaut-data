package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.jdbc.entities.User;

public interface UserRepository extends CrudRepository<User, Long> {

}
