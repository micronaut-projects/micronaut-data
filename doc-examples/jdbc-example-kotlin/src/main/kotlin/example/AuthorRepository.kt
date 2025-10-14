package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.jpa.kotlin.KotlinJpaSpecificationExecutor
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

@JdbcRepository(dialect = Dialect.H2)
@Join(value = "genres", type = Join.Type.LEFT_FETCH)
interface AuthorRepository : KotlinCrudRepository<Author, Long>, KotlinJpaSpecificationExecutor<Author>
