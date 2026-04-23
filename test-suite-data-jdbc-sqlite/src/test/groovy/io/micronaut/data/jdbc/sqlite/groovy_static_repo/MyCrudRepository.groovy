package io.micronaut.data.jdbc.sqlite.groovy_static_repo

import groovy.transform.CompileStatic
import io.micronaut.data.annotation.Id
import io.micronaut.data.repository.CrudRepository

@CompileStatic
interface MyCrudRepository<E, PK> extends CrudRepository<E, PK> {
    void update(@Id UUID id, String name)

}