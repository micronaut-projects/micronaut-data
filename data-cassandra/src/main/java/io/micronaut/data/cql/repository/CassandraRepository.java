package io.micronaut.data.cql.repository;

import io.micronaut.data.repository.CrudRepository;

public interface CassandraRepository<E,ID> extends CrudRepository<E, ID> {
}
