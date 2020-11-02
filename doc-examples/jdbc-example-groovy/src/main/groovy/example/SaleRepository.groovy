
package example

import edu.umd.cs.findbugs.annotations.NonNull
import io.micronaut.data.annotation.Join
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

import javax.validation.constraints.NotNull

@JdbcRepository(dialect = Dialect.H2)
interface SaleRepository extends CrudRepository<Sale, Long> {

    @NonNull
    @Override
    @Join("product")
    @Join("product.manufacturer")
    Optional<Sale> findById(@NonNull @NotNull Long aLong)
}
