package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.repeatable.JoinSpecifications
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.KotlinCrudRepository
import java.util.*

@JdbcRepository(dialect = Dialect.H2)
interface StudentRepository : KotlinCrudRepository<Student, Long> {

    @Join("courses")
    override fun findById(id: Long): Student?

    @JoinSpecifications(
            Join(value = "courses", type = Join.Type.LEFT_FETCH),
            Join(value = "ratings", type = Join.Type.LEFT_FETCH),
            Join(value = "ratings.course", type = Join.Type.LEFT_FETCH),
            Join(value = "ratings.student", type = Join.Type.LEFT_FETCH)
    )
    fun queryById(aLong: Long): Student?
}
