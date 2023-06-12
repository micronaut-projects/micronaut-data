package example

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.tck.repositories.PersonCoroutineRepository
import io.micronaut.data.tck.repositories.PersonCustomDbCoroutineRepository

@R2dbcRepository(dataSource = "custom", dialect = Dialect.MYSQL)
interface R2dbcPersonCustomDbCoroutineRepository : PersonCustomDbCoroutineRepository
