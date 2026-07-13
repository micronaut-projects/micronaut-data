package io.micronaut.data.jdbc.sqlite.groovy_static_repo;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.repository.CrudRepository;

import java.util.UUID;

interface MyCrudRepository<E, PK> extends CrudRepository<E, PK> {

    void update(@Id UUID id, String name);
}
