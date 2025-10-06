package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.GenericRepository

@JdbcRepository(dialect = Dialect.H2)
interface SampleEntityRepository : GenericRepository<SampleEntity, Long> {
    fun getById(id: Long): SampleEntity
    fun save(entity: SampleEntity): SampleEntity
    fun update(entity: SampleEntity): SampleEntity
}
