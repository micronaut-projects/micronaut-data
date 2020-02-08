package io.micronaut.data.cql.repository;

import java.util.Map;

public interface MapIdCassandraRepository<E> extends CassandraRepository<E, Map<String, Object>> {
}
