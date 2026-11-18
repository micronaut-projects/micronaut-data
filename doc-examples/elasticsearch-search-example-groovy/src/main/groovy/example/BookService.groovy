package example

import io.micronaut.data.model.vector.search.SearchResult
import io.micronaut.data.model.vector.search.SearchResults
import jakarta.inject.Singleton

@Singleton
class BookService {

    private final BookRepository bookRepository

    BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository
    }

    List<String> titlesMatching(String query) {
        // tag::search[]
        SearchResults<Book> results = bookRepository.search(query) // <1>
        for (SearchResult<Book> hit : results) {
            Book book = hit.entity() // <2>
            double score = hit.score().value() // <3>
        }
        long hits = bookRepository.countHits(query) // <4>
        // end::search[]
        return results.results()*.entity()*.title
    }
}
