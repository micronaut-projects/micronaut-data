package example

import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.repeatable.JoinSpecifications
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.util.*

@JdbcRepository(dialect = Dialect.H2)
interface StudentRepository : CrudRepository<Student, Long> {
    @Join("courses")
    override fun findById(@NonNull id: Long?): Optional<Student>

    @JoinSpecifications(
            Join(value = "courses", type = Join.Type.LEFT_FETCH),
            Join(value = "ratings", type = Join.Type.LEFT_FETCH),
            Join(value = "ratings.course", type = Join.Type.LEFT_FETCH),
            Join(value = "ratings.student", type = Join.Type.LEFT_FETCH)
    )
    fun queryById(aLong: Long): Optional<Student>
}