package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Repository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.CrudRepository
import java.util.Optional

@R2dbcRepository(dataSource = "custom", dialect = Dialect.MYSQL)
abstract class ParentRepositoryForCustomDb : CrudRepository<Parent, Int> {

    @Join(value = "children", type = Join.Type.FETCH)
    abstract override fun findById(id: Int): Optional<Parent>

}
