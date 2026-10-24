package example;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.search.MatchMode;
import io.micronaut.data.model.search.SearchQuery;
import io.micronaut.data.model.vector.search.SearchResult;
import io.micronaut.data.model.vector.search.SearchResults;
import jakarta.inject.Singleton;

import java.util.ArrayList;
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

    public List<String> titlesContainingEveryWord(String query) {
        // tag::searchQuery[]
        SearchQuery searchQuery = SearchQuery.builder(query) // <1>
            .fields("title") // <2>
            .mode(MatchMode.ALL) // <3>
            .build();
        SearchResults<Book> results = bookRepository.search(searchQuery); // <4>
        SearchResults<Book> firstPage = bookRepository.search(searchQuery, Pageable.from(0, 10)); // <5>
        // end::searchQuery[]
        return results.results().stream().map(hit -> hit.entity().title()).toList();
    }

    public List<String> highlightedTitles(String query) {
        // tag::highlight[]
        SearchQuery searchQuery = SearchQuery.builder(query)
            .highlight("title") // <1>
            .build();
        List<String> fragments = new ArrayList<>();
        for (SearchResult<Book> hit : bookRepository.search(searchQuery)) {
            fragments.addAll(hit.highlights().get("title")); // <2>
        }
        // end::highlight[]
        return fragments;
    }
}
