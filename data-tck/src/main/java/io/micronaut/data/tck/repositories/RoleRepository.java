package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.jdbc.entities.Role;

public interface RoleRepository extends CrudRepository<Role, Long> {

}
