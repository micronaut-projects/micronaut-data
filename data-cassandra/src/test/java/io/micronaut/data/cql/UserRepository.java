package io.micronaut.data.cql;

import io.micronaut.data.cql.annotation.CqlRepository;
import io.micronaut.data.repository.GenericRepository;

@CqlRepository()
public interface UserRepository extends GenericRepository<User, Long> {

    User save(String username);

    User findById(Long id);
}
