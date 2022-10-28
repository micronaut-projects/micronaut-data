package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

@JdbcRepository(dataSource = "custom", dialect = Dialect.H2)
abstract class ParentRepositoryForCustomDb : KotlinCrudRepository<Parent, Int> {

    @Join(value = "children", type = Join.Type.FETCH)
    abstract override fun findById(id: Int): Parent?

}
