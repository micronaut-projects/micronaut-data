package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor

@JdbcRepository(dialect = Dialect.H2)
@Join(value = "genres", type = Join.Type.LEFT_FETCH)
interface AuthorRepository : CrudRepository<Author, Long>, JpaSpecificationExecutor<Author>
