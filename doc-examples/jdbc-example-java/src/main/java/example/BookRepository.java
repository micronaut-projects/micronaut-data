
// tag::repository[]
package example;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.sql.Procedure;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.*;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import java.util.List;


@JdbcRepository(dialect = Dialect.H2)        // <1>
interface BookRepository extends CrudRepository<Book, Long> { // <2>
// end::repository[]

    // tag::simple[]
    Book findByTitle(String title);

    Book getByTitle(String title);

    Book retrieveByTitle(String title);
    // end::simple[]

    // tag::greaterthan[]
    List<Book> findByPagesGreaterThan(int pageCount);
    // end::greaterthan[]

    // tag::logical[]
    List<Book> findByPagesGreaterThanOrTitleLike(int pageCount, String title);
    // end::logical[]

    // tag::simple-alt[]
    // tag::repository[]
    Book find(String title);
    // end::simple-alt[]
    // end::repository[]

    // tag::pageable[]
    List<Book> findByPagesGreaterThan(int pageCount, Pageable pageable);

    Page<Book> findByTitleLike(String title, Pageable pageable);

    Slice<Book> list(Pageable pageable);
    // end::pageable[]

    // tag::cursored-pageable[]
    CursoredPage<Book> find(CursoredPageable pageable); // <1>

    CursoredPage<Book> findByPagesBetween(int minPageCount, int maxPageCount, Pageable pageable); // <2>

    Page<Book> findByTitleStartingWith(String title, Pageable pageable); // <3>
    // end::cursored-pageable[]

    // tag::simple-projection[]
    List<String> findTitleByPagesGreaterThan(int pageCount);
    // end::simple-projection[]

    // tag::top-projection[]
    List<Book> findTop3ByTitleLike(String title);
    // end::top-projection[]

    // tag::ordering[]
    List<Book> listOrderByTitle();

    List<Book> listOrderByTitleDesc();
    // end::ordering[]

    // tag::explicit[]
    @Query("SELECT * FROM Book AS b WHERE b.title = :t ORDER BY b.title")
    List<Book> listBooks(String t);
    // end::explicit[]

    // tag::save[]
    Book persist(Book entity);
    // end::save[]

    // tag::save2[]
    Book persist(String title, int pages);
    // end::save2[]

    @Query("INSERT INTO Book(title, pages) VALUES (:title, :pages)")
    @ParameterExpression(name = "title", expression = "#{book.title + 'ABC'}")
    @ParameterExpression(name = "pages", expression = "#{book.pages}")
    void insertCustomExp(Book book);

    // tag::update[]
    void update(@Id Long id, int pages);

    void update(@Id Long id, String title);
    // end::update[]

    // tag::update2[]
    void updateByTitle(String title, int pages);
    // end::update2[]

    // tag::deleteall[]
    void deleteAll();
    // end::deleteall[]

    // tag::deleteone[]
    void delete(String title);
    // end::deleteone[]

    // tag::deleteby[]
    void deleteByTitleLike(String title);
    // end::deleteby[]

    // tag::dto[]
    BookDTO findOne(String title);
    // end::dto[]

    // tag::native[]
    @Query("select * from book b where b.title like :title limit 5")
    List<Book> findBooks(String title);
    // end::native[]

    // tag::procedure[]
    @Procedure
    Long calculateSum(@NonNull Long bookId);
    // end::procedure[]

    // tag::onetomanycustom[]
    @Query("""
        SELECT book_.*,
               reviews_.id AS reviews_id, reviews_.reviewer AS reviews_reviewer,
               reviews_.content AS reviews_content, reviews_.book_id AS reviews_book_id
        FROM book book_ INNER JOIN review reviews_ ON book_.id = reviews_.book_id
        WHERE book_.title = :title
        """)
    @Join("reviews")
    List<Book> searchBooksByTitle(String title);
    // end::onetomanycustom[]


// tag::repository[]
}
// end::repository[]
