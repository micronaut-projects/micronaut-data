
package example;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.data.repository.CrudRepository;
import reactor.core.publisher.Flux;

import java.util.List;

@R2dbcRepository(dialect = Dialect.H2, dataSource = "r2dbc")
public abstract class AbstractR2dbcBookRepository implements CrudRepository<Book, Long> {

    private final R2dbcOperations r2dbcOperations;

    public AbstractR2dbcBookRepository(R2dbcOperations r2dbcOperations) {
        this.r2dbcOperations = r2dbcOperations;
    }

    public List<Book> findByTitle(String title) {
        String sql = "SELECT * FROM Book AS book WHERE book.title = ?";
        return Flux.from(r2dbcOperations.withConnection(connection -> {
            return Flux.from(connection.createStatement(sql).bind(0, title).execute())
                .flatMap(result -> result.map((row, rowMetadata) -> {
                    Book book = new Book(row.get("title", String.class), row.get("pages", Integer.class));
                    book.setId(row.get("id", Long.class));
                    return book;
                }));
        })).collectList().block();
    }
}
