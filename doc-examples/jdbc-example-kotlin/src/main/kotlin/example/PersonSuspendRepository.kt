package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.jpa.kotlin.CoroutineJpaSpecificationExecutor
import io.micronaut.data.repository.kotlin.CoroutinePageableCrudRepository

@JdbcRepository(dialect = Dialect.H2)
interface PersonSuspendRepository : CoroutinePageableCrudRepository<Person, Long>, CoroutineJpaSpecificationExecutor<Person> {

    suspend fun queryById(id: Long): Person
}