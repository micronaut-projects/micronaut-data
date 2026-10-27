package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(transactional = false)
class BookRepositorySpec extends Specification implements TestPropertyProvider {

    @Shared
    @AutoCleanup
    ElasticsearchContainer elasticsearch = new ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.1.0"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")

    @Override
    Map<String, String> getProperties() {
        elasticsearch.start()
        return ["elasticsearch.http-hosts": "http://" + elasticsearch.httpHostAddress]
    }


    @Inject
    BookRepository bookRepository

    @Inject
    BookService bookService

    @Inject
    ReactiveBookRepository reactiveBookRepository

    void setup() {
        bookRepository.deleteAll()
        bookRepository.saveAll([
            new Book("Cooking with Coffee", "Recipes and desserts made with coffee beans", "Bean"),
            new Book("Baking Bread", "Sourdough and yeast breads for the home baker", "Dough"),
            new Book("The Coffee Roaster", "Roasting coffee at home, from green beans to espresso", "Roast")
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

    void "reactive search"() {
        expect:
        reactiveBookRepository.searchBySearch("coffee").block().totalHits() == 2
        reactiveBookRepository.findByTitleSearch("roaster").collectList().block()*.title == ["The Coffee Roaster"]
        reactiveBookRepository.count().block() == 3L
        reactiveBookRepository.search("coffee").block().totalHits() == 2
        reactiveBookRepository.countHits("coffee").block() == 2L
    }

    void "derived search finders"() {
        expect:
        bookRepository.findByTitleSearch("roaster").size() == 1
        bookRepository.searchByTitleSearchAll("coffee roaster").results()[0].entity().title == "The Coffee Roaster"
        bookRepository.findByDescriptionSearchPhrase("coffee beans").size() == 1
        bookRepository.findByDescriptionSearchPhrase("beans coffee").isEmpty()
        bookRepository.findByTitleSearchPrefix("coff").size() == 2
        bookRepository.findBySearchAndAuthor("coffee", "Bean").size() == 1
    }
}
