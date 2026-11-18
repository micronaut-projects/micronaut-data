package example;

import io.micronaut.data.model.search.SearchQuery;
import io.micronaut.data.model.vector.search.SearchResult;
import io.micronaut.data.model.vector.search.SearchResults;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<String> titlesMatching(String query) {
        // tag::search[]
        SearchResults<Book> results = bookRepository.search(query); // <1>
        for (SearchResult<Book> hit : results) {
            Book book = hit.entity(); // <2>
            double score = hit.score().value(); // <3>
        }
        long hits = bookRepository.countHits(query); // <4>
        // end::search[]
        return results.results().stream().map(hit -> hit.entity().title()).toList();
    }

    public List<String> titlesLike(String query) {
        // tag::options[]
        SearchQuery boosted = SearchQuery.builder(query)
            .boost("title", 5) // <1>
            .fuzzy(2) // <2>
            .build();
        SearchResults<Book> results = bookRepository.search(boosted);
        // end::options[]
        return results.results().stream().map(hit -> hit.entity().title()).toList();
    }
}
