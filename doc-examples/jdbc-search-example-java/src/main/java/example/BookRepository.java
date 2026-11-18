package example;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.SearchRepository;

import java.util.List;

// tag::repository[]
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface BookRepository extends CrudRepository<Book, Long>, SearchRepository<Book, Long> { // <1>

    List<Book> findByTitleSearch(String query); // <2>

    SearchResults<Book> searchByTitleSearchAll(String query); // <3>

    List<Book> findByDescriptionSearchPhrase(String query); // <4>

    List<Book> findByTitleSearchPrefix(String query); // <5>

    List<Book> findBySearchAndAuthor(String query, String author); // <6>
}
// end::repository[]
