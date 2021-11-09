package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.kotlin.CoroutineJpaSpecificationExecutor

@JdbcRepository(dialect = Dialect.H2)
interface PersonSuspendRepository : CrudRepository<Person, Long>, CoroutineJpaSpecificationExecutor<Person>