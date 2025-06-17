
// tag::repository[]
package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.ParameterExpression;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import jakarta.data.Sort;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.H2)        // <1>
interface BookRepository extends CrudRepository<Book, Long> { // <2>
// end::repository[]

    // tag::simple[]
    Book findByTitle(String title);

    Book getByTitle(String title);

    Book retrieveByTitle(String title);

    long count();

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
    List<Book> findByPagesGreaterThan(int pageCount, PageRequest pageRequest);

    Page<Book> findByTitleLike(String title, PageRequest pageRequest);

    Page<Book> list(PageRequest pageRequest);
    // end::pageable[]

    // tag::cursored-pageable[]
    CursoredPage<Book> find(PageRequest pageRequest);

    CursoredPage<Book> find(PageRequest pageRequest, Sort<?> sort); // <1>

    CursoredPage<Book> findByPagesBetween(int minPageCount, int maxPageCount, PageRequest pageRequest); // <2>

    Page<Book> findByTitleStartingWith(String title, PageRequest pageRequest); // <3>
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


// tag::repository[]
}
// end::repository[]
