package io.micronaut.data.jdbc.sqlite.groovy_static_repo

import groovy.transform.CompileStatic
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect

@JdbcRepository(dialect = Dialect.ANSI)
@CompileStatic
interface TestEntityRepository extends MyCrudRepository<GTestEntity, UUID> {

}
