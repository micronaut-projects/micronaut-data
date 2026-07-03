package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.tck.repositories.AuthenticationRepository;

@Repository
public interface JpaAuthenticationRepository extends AuthenticationRepository {
}
