
package example;

import io.micronaut.data.annotation.WithoutTenantId;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
interface BookRepository extends CrudRepository<Book, Long> {

    @WithoutTenantId
    List<Book> findAll$WithoutTenancy();

}
