package example;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Slice;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
public interface PagedBookRepository extends CrudRepository<Book, Long> {

    // tag::explicit[]
    @Query(value = "SELECT book_.* FROM book book_ WHERE book_.pages > :pages", // <1>
           countQuery = "SELECT COUNT(*) FROM book book_ WHERE book_.pages > :pages") // <2>
    Page<Book> findLongBooks(int pages, Pageable pageable);

    @Query("SELECT book_.* FROM book book_ WHERE book_.pages > :pages") // <3>
    Slice<Book> sliceLongBooks(int pages, Pageable pageable);

    @Query("SELECT book_.* FROM book book_ WHERE book_.pages > :pages") // <3>
    List<Book> listLongBooks(int pages, Pageable pageable);

    @Query("SELECT book_.* FROM book book_ WHERE book_.pages > :pages") // <4>
    List<Book> listLongBooksSorted(int pages, Sort sort);
    // end::explicit[]

    // tag::native[]
    @Query(value = "SELECT * FROM book b WHERE b.title LIKE :title",
           countQuery = "SELECT COUNT(*) FROM book b WHERE b.title LIKE :title",
           nativeQuery = true) // <1>
    Page<Book> searchByTitle(String title, Pageable pageable);
    // end::native[]

    Page<BookSummary> list(Pageable pageable);
}
