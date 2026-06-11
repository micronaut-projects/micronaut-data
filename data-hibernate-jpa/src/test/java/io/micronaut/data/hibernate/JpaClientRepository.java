package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.tck.repositories.ClientRepository;

@Repository
public interface JpaClientRepository extends ClientRepository {
}
