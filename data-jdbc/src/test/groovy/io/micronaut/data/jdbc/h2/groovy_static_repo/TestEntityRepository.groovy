package io.micronaut.data.jdbc.h2.groovy_static_repo

import groovy.transform.CompileStatic
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect

@JdbcRepository(dialect = Dialect.H2)
@CompileStatic
interface TestEntityRepository extends MyCrudRepository<GTestEntity, UUID> {

}