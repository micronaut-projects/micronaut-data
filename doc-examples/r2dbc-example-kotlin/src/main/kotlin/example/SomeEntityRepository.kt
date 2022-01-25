package example

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository

@R2dbcRepository(dialect = Dialect.POSTGRES)
interface SomeEntityRepository : ReactorCrudRepository<SomeEntity, Long>