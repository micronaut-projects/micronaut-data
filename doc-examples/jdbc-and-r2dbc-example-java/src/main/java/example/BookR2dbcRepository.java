
package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@R2dbcRepository(dialect = Dialect.H2, dataSource = "r2dbc")
interface BookR2dbcRepository extends CrudRepository<Book, Long> {

    Book find(String title);

    List<Book> findByPagesGreaterThan(int pageCount, Pageable pageable);

    Page<Book> findByTitleLike(String title, Pageable pageable);

    Slice<Book> list(Pageable pageable);

    void update(@Id Long id, int pages);

    void update(@Id Long id, String title);

    void deleteAll();

    void delete(String title);

}
