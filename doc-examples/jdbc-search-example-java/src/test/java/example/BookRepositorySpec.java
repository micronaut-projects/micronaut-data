package example;

import io.micronaut.data.model.search.SearchQuery;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
class BookRepositorySpec {

    @Inject
    BookRepository bookRepository;

    @Inject
    BookService bookService;

    @BeforeEach
    void setup() {
        bookRepository.deleteAll();
        bookRepository.saveAll(List.of(
            new Book(null, "Cooking with Coffee", "Recipes and desserts made with coffee beans", "Bean", Vector.of(1f, 0f, 0f)),
            new Book(null, "Baking Bread", "Sourdough and yeast breads for the home baker", "Dough", Vector.of(0.2f, 0.9f, 0f)),
            new Book(null, "The Coffee Roaster", "Roasting coffee at home, from green beans to espresso", "Roast", Vector.of(0.9f, 0.1f, 0f))
        ));
    }

    @Test
    void searchOrdersByRelevance() {
        SearchResults<Book> results = bookRepository.search("coffee");
        assertEquals(2, results.totalHits());
        assertTrue(results.results().get(0).score().value() >= results.results().get(1).score().value());
        assertEquals(2, bookRepository.countHits("coffee"));
        assertEquals(List.of("Baking Bread"), bookService.titlesMatching("bread OR sourdough"));
        assertEquals(1, bookRepository.search("coffee -desserts").totalHits());
    }

    @Test
    void derivedSearchFinders() {
        assertEquals(1, bookRepository.findByTitleSearch("roaster").size());
        assertEquals("The Coffee Roaster", bookRepository.searchByTitleSearchAll("coffee roaster").results().get(0).entity().title());
        assertEquals(1, bookRepository.findByDescriptionSearchPhrase("coffee beans").size());
        assertEquals(0, bookRepository.findByDescriptionSearchPhrase("beans coffee").size());
        assertEquals(2, bookRepository.findByTitleSearchPrefix("coff").size());
        assertEquals(1, bookRepository.findBySearchAndAuthor("coffee", "Bean").size());
        assertEquals(2, bookRepository.searchByTitleSearch("coffee", new Score(0)).totalHits());
        assertEquals(0, bookRepository.searchByTitleSearch("coffee", new Score(100)).totalHits());
    }

    @Test
    void programmaticSearchQuery() {
        assertEquals(List.of("The Coffee Roaster"), bookService.titlesContainingEveryWord("coffee roaster"));
        assertEquals(List.of(), bookService.titlesContainingEveryWord("coffee beans"));
        assertEquals(2, bookRepository.search(SearchQuery.of("coffee")).totalHits());
    }

    @Test
    void hybridSearch() {
        // the bread book is the only text hit and third by vector similarity, so it collects both ranks
        assertEquals(List.of("Baking Bread", "Cooking with Coffee", "The Coffee Roaster"), bookService.hybridTitles("bread", Vector.of(1f, 0f, 0f)));
    }

    @Test
    void highlighting() {
        List<String> fragments = bookService.highlightedTitles("coffee");
        assertEquals(2, fragments.size());
        assertTrue(fragments.stream().allMatch(f -> f.contains("<em>Coffee</em>")));
    }
}
