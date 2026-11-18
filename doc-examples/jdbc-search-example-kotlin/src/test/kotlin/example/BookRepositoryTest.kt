package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class BookRepositoryTest {

    @Inject
    lateinit var bookRepository: BookRepository

    @Inject
    lateinit var bookService: BookService

    @BeforeEach
    fun setup() {
        bookRepository.deleteAll()
        bookRepository.saveAll(listOf(
            Book(title = "Cooking with Coffee", description = "Recipes and desserts made with coffee beans", author = "Bean"),
            Book(title = "Baking Bread", description = "Sourdough and yeast breads for the home baker", author = "Dough"),
            Book(title = "The Coffee Roaster", description = "Roasting coffee at home, from green beans to espresso", author = "Roast")
        ))
    }

    @Test
    fun searchOrdersByRelevance() {
        val results = bookRepository.search("coffee")
        assertEquals(2, results.totalHits())
        assertTrue(results.results()[0].score().value() >= results.results()[1].score().value())
        assertEquals(2, bookRepository.countHits("coffee"))
        assertEquals(listOf("Baking Bread"), bookService.titlesMatching("bread OR sourdough"))
        assertEquals(1, bookRepository.search("coffee -desserts").totalHits())
    }

    @Test
    fun derivedSearchFinders() {
        assertEquals(1, bookRepository.findByTitleSearch("roaster").size)
        assertEquals("The Coffee Roaster", bookRepository.searchByTitleSearchAll("coffee roaster").results()[0].entity().title)
        assertEquals(1, bookRepository.findByDescriptionSearchPhrase("coffee beans").size)
        assertEquals(0, bookRepository.findByDescriptionSearchPhrase("beans coffee").size)
        assertEquals(2, bookRepository.findByTitleSearchPrefix("coff").size)
        assertEquals(1, bookRepository.findBySearchAndAuthor("coffee", "Bean").size)
    }
}
