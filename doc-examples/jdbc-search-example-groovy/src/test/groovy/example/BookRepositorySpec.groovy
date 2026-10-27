package example

import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.Vector
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class BookRepositorySpec extends Specification {

    @Inject
    BookRepository bookRepository

    @Inject
    BookService bookService

    void setup() {
        bookRepository.deleteAll()
        bookRepository.saveAll([
            new Book("Cooking with Coffee", "Recipes and desserts made with coffee beans", "Bean", Vector.of(1f, 0f, 0f)),
            new Book("Baking Bread", "Sourdough and yeast breads for the home baker", "Dough", Vector.of(0.2f, 0.9f, 0f)),
            new Book("The Coffee Roaster", "Roasting coffee at home, from green beans to espresso", "Roast", Vector.of(0.9f, 0.1f, 0f))
        ])
    }

    void "search orders by relevance"() {
        when:
        def results = bookRepository.search("coffee")

        then:
        results.totalHits() == 2
        results.results()[0].score().value() >= results.results()[1].score().value()
        bookRepository.countHits("coffee") == 2
        bookService.titlesMatching("bread OR sourdough") == ["Baking Bread"]
        bookRepository.search("coffee -desserts").totalHits() == 1
    }

    void "derived search finders"() {
        expect:
        bookRepository.findByTitleSearch("roaster").size() == 1
        bookRepository.searchByTitleSearchAll("coffee roaster").results()[0].entity().title == "The Coffee Roaster"
        bookRepository.findByDescriptionSearchPhrase("coffee beans").size() == 1
        bookRepository.findByDescriptionSearchPhrase("beans coffee").isEmpty()
        bookRepository.findByTitleSearchPrefix("coff").size() == 2
        bookRepository.findBySearchAndAuthor("coffee", "Bean").size() == 1
        bookRepository.searchByTitleSearch("coffee", new Score(0)).totalHits() == 2
        bookRepository.searchByTitleSearch("coffee", new Score(100)).totalHits() == 0
    }

    void "programmatic search query"() {
        expect:
        bookService.titlesContainingEveryWord("coffee roaster") == ["The Coffee Roaster"]
        bookService.titlesContainingEveryWord("coffee beans").isEmpty()
        bookRepository.search(SearchQuery.of("coffee")).totalHits() == 2
    }

    void "hybrid search"() {
        expect:
        bookService.hybridTitles("bread", Vector.of(1f, 0f, 0f)) == ["Baking Bread", "Cooking with Coffee", "The Coffee Roaster"]
    }

    void "highlighting"() {
        when:
        def fragments = bookService.highlightedTitles("coffee")

        then:
        fragments.size() == 2
        fragments.every { it.contains("<em>Coffee</em>") }
    }
}
