package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // TestPropertyProvider needs one instance per class
class BookRepositoryTest : TestPropertyProvider {

    companion object {
        val elasticsearch: ElasticsearchContainer = ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.1.0"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
    }

    override fun getProperties(): Map<String, String> {
        elasticsearch.start()
        return mapOf("elasticsearch.http-hosts" to "http://" + elasticsearch.httpHostAddress)
    }


    @Inject
    lateinit var bookRepository: BookRepository

    @Inject
    lateinit var bookService: BookService

    @Inject
    lateinit var reactiveBookRepository: ReactiveBookRepository

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
    fun reactiveSearch() {
        assertEquals(2, reactiveBookRepository.searchBySearch("coffee").block()!!.totalHits())
        assertEquals(listOf("The Coffee Roaster"), reactiveBookRepository.findByTitleSearch("roaster").map { it.title }.collectList().block())
        assertEquals(3, reactiveBookRepository.count().block())
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
