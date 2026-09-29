package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

// tag::forUpdate[]
@JdbcRepository(dialect = Dialect.H2)
interface ProductLockingRepository extends CrudRepository<Product, Long> {

    @Join("manufacturer")
    Optional<Product> findByIdForUpdate(Long id)

    @Join("manufacturer")
    List<Product> findAllOrderByNameForUpdate()

    @Join("manufacturer")
    List<Product> findByNameForUpdate(String name)
}
// end::forUpdate[]
