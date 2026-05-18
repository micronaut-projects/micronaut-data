package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.util.*

@JdbcRepository(dialect = Dialect.H2)
abstract class FooRepository : CrudRepository<Foo, Long> {

    @Join(value = "foo", type = Join.Type.LEFT_FETCH)
    @Join(value = "bar", type = Join.Type.LEFT_FETCH)
    abstract override fun findById(id: Long): Optional<Foo>

    abstract fun update(@Id id: Long, foo: Foo)

    abstract fun update(@Id id: Long, bar: Bar)

    abstract fun update(@Id id: Long, name: String)
}
