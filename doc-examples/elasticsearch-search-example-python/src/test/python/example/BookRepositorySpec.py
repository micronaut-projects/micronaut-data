from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.test.support import TestPropertyProvider
from org.testcontainers.elasticsearch import ElasticsearchContainer
from org.testcontainers.utility import DockerImageName
from org.junit.jupiter.api import BeforeEach, Test

from example.Book import Book
from example.BookRepository import BookRepository
from example.BookService import BookService
from example.ReactiveBookRepository import ReactiveBookRepository


ELASTICSEARCH = (ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.1.0"))
                 .withEnv("xpack.security.enabled", "false")
                 .withEnv("discovery.type", "single-node")
                 .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m"))


@MicronautTest(transactional=False)
class BookRepositorySpec(TestPropertyProvider):

    def getProperties(self) -> dict[str, str]:
        ELASTICSEARCH.start()
        return {"elasticsearch.http-hosts": "http://" + ELASTICSEARCH.getHttpHostAddress()}

    repository: Annotated[BookRepository, Inject]
    service: Annotated[BookService, Inject]
    reactive_repository: Annotated[ReactiveBookRepository, Inject]

    @BeforeEach
    def setup(self):
        self.repository.deleteAll()
        self.repository.saveAll([
            Book(None, "Cooking with Coffee", "Recipes and desserts made with coffee beans", "Bean"),
            Book(None, "Baking Bread", "Sourdough and yeast breads for the home baker", "Dough"),
            Book(None, "The Coffee Roaster", "Roasting coffee at home, from green beans to espresso", "Roast"),
        ])

    @Test
    def searchOrdersByRelevance(self):
        results = self.repository.search("coffee")
        assert results.totalHits() == 2
        assert results.results()[0].score().value() >= results.results()[1].score().value()
        assert self.repository.countHits("coffee") == 2
        assert self.service.titles_matching("bread OR sourdough") == ["Baking Bread"]
        assert self.repository.search("coffee -desserts").totalHits() == 1

    @Test
    def reactiveSearch(self):
        assert self.reactive_repository.searchBySearch("coffee").block().totalHits() == 2
        assert [b.title for b in self.reactive_repository.findByTitleSearch("roaster").collectList().block()] == ["The Coffee Roaster"]
        assert self.reactive_repository.count().block() == 3
        assert self.reactive_repository.search("coffee").block().totalHits() == 2
        assert self.reactive_repository.countHits("coffee").block() == 2

    @Test
    def derivedSearchFinders(self):
        assert len(self.repository.findByTitleSearch("roaster")) == 1
        assert self.repository.searchByTitleSearchAll("coffee roaster").results()[0].entity().title == "The Coffee Roaster"
        assert len(self.repository.findByDescriptionSearchPhrase("coffee beans")) == 1
        assert len(self.repository.findByDescriptionSearchPhrase("beans coffee")) == 0
        assert len(self.repository.findByTitleSearchPrefix("coff")) == 2
        assert len(self.repository.findBySearchAndAuthor("coffee", "Bean")) == 1
