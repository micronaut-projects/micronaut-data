
package example;

import io.micronaut.data.annotation.WithoutTenantId;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;

@R2dbcRepository(dialect = Dialect.MYSQL)
interface BookRepository extends ReactorCrudRepository<Book, Long> {

    @WithoutTenantId
    Flux<Book> findAll$NoTenancy();

}
