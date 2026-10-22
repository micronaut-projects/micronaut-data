package example;

import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
class BookRepositorySpec implements ElasticsearchTestPropertyProvider {

    @Inject
    BookRepository bookRepository;

    @Inject
    BookService bookService;

    @BeforeEach
    void setup() {
        bookRepository.deleteAll();
        bookRepository.saveAll(List.of(
            new Book(null, "Cooking with Coffee", "Recipes and desserts made with coffee beans", "Bean"),
            new Book(null, "Baking Bread", "Sourdough and yeast breads for the home baker", "Dough"),
            new Book(null, "The Coffee Roaster", "Roasting coffee at home, from green beans to espresso", "Roast")
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
    }
}
