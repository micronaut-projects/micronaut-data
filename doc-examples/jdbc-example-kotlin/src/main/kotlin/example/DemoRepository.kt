package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.reactive.ReactorCrudRepository

@JdbcRepository(dialect = Dialect.H2)
interface DemoRepository : ReactorCrudRepository<DemoEntity, Long>
