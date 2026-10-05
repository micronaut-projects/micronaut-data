package example

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import jakarta.data.Sort
import jakarta.data.page.CursoredPage
import jakarta.data.page.Page
import jakarta.data.page.PageRequest
import jakarta.data.repository.CrudRepository

@JdbcRepository(dialect = Dialect.H2)
interface BookRepository extends CrudRepository<Book, Long> {

    long count()

    void deleteAll()

    List<Book> findByPagesGreaterThan(int pageCount, PageRequest pageRequest)

    Page<Book> findByTitleLike(String title, PageRequest pageRequest)

    Page<Book> list(PageRequest pageRequest)

    CursoredPage<Book> find(PageRequest pageRequest, Sort<?> sort)

    CursoredPage<Book> findByPagesBetween(int minPageCount, int maxPageCount, PageRequest pageRequest)

    Page<Book> findByTitleStartingWith(String title, PageRequest pageRequest)
}
