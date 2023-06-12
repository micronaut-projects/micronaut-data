package example

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.tck.repositories.PersonCoroutineRepository
import io.micronaut.data.tck.repositories.PersonRepository

@R2dbcRepository(dialect = Dialect.MYSQL)
interface R2dbcPersonRepository : PersonRepository
