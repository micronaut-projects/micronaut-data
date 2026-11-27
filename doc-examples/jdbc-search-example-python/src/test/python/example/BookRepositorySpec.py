from typing import Annotated

from jakarta.inject import Inject
from micronaut.data.model.search import SearchQuery
from micronaut.data.model.vector.search import Score
from micronaut.data.model.vector import Vector
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import BeforeEach, Test

from example.Book import Book
from example.BookRepository import BookRepository
from example.BookService import BookService


@MicronautTest(transactional=False)
class BookRepositorySpec:

    repository: Annotated[BookRepository, Inject]
    service: Annotated[BookService, Inject]

    @BeforeEach
    def setup(self):
        self.repository.deleteAll()
        self.repository.saveAll([
            Book(None, "Cooking with Coffee", "Recipes and desserts made with coffee beans", "Bean", Vector.of(1.0, 0.0, 0.0)),
            Book(None, "Baking Bread", "Sourdough and yeast breads for the home baker", "Dough", Vector.of(0.2, 0.9, 0.0)),
            Book(None, "The Coffee Roaster", "Roasting coffee at home, from green beans to espresso", "Roast", Vector.of(0.9, 0.1, 0.0)),
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
        assert self.repository.searchByTitleSearch("coffee", Score(0.0)).totalHits() == 2
        assert self.repository.searchByTitleSearch("coffee", Score(100.0)).totalHits() == 0

    @Test
    def programmaticSearchQuery(self):
        assert self.service.titles_containing_every_word("coffee roaster") == ["The Coffee Roaster"]
        assert self.service.titles_containing_every_word("coffee beans") == []
        assert self.repository.search(SearchQuery.of("coffee")).totalHits() == 2

    @Test
    def hybridSearch(self):
        assert self.service.hybrid_titles("bread", Vector.of(1.0, 0.0, 0.0)) == ["Baking Bread", "Cooking with Coffee", "The Coffee Roaster"]

    @Test
    def highlighting(self):
        fragments = self.service.highlighted_titles("coffee")
        assert len(fragments) == 2
        assert all("<em>Coffee</em>" in f for f in fragments)
