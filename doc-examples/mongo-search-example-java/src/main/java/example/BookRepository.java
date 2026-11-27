package example;

import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.SearchRepository;

import java.util.List;

// tag::repository[]
@MongoRepository
public interface BookRepository extends CrudRepository<Book, String>, SearchRepository<Book, String> { // <1>

    List<Book> findByTitleSearch(String query); // <2>

    SearchResults<Book> searchByTitleSearchAll(String query); // <3>

    List<Book> findByDescriptionSearchPhrase(String query); // <4>

    List<Book> findBySearchAndAuthor(String query, String author); // <5>
}
// end::repository[]
