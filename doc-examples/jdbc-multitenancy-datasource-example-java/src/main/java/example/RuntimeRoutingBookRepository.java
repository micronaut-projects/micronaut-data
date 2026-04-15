package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

@Requires(property = "example.runtime-routing.enabled", value = "true")
// tag::repository[]
@JdbcRepository(dialect = Dialect.H2)
interface RuntimeRoutingBookRepository extends CrudRepository<Book, Long> {
}
// end::repository[]
