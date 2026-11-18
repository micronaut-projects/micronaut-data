from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import BeforeEach, Test

from example.Book import Book
from example.BookRepository import BookRepository
from example.BookService import BookService


# support.ElasticsearchConfigurer starts the Elasticsearch container for the test context
@MicronautTest(transactional=False)
class BookRepositorySpec:

    repository: Annotated[BookRepository, Inject]
    service: Annotated[BookService, Inject]

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
    def derivedSearchFinders(self):
        assert len(self.repository.findByTitleSearch("roaster")) == 1
        assert self.repository.searchByTitleSearchAll("coffee roaster").results()[0].entity().title == "The Coffee Roaster"
        assert len(self.repository.findByDescriptionSearchPhrase("coffee beans")) == 1
        assert len(self.repository.findByDescriptionSearchPhrase("beans coffee")) == 0
        assert len(self.repository.findByTitleSearchPrefix("coff")) == 2
        assert len(self.repository.findBySearchAndAuthor("coffee", "Bean")) == 1
