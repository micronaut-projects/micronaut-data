from jakarta.inject import Singleton
from micronaut.data.model import Pageable
from micronaut.data.model.search import MatchMode, SearchQuery

from example.BookRepository import BookRepository


@Singleton
class BookService:

    def __init__(self, repository: BookRepository):
        self.repository = repository

    def titles_matching(self, query: str) -> list[str]:
        # tag::search[]
        results = self.repository.search(query)  # <1>
        for hit in results:
            book = hit.entity()  # <2>
            score = hit.score().value()  # <3>
        hits = self.repository.countHits(query)  # <4>
        # end::search[]
        return [hit.entity().title for hit in results]

    def titles_containing_every_word(self, query: str) -> list[str]:
        # tag::searchQuery[]
        search_query = (SearchQuery.builder(query)  # <1>
                        .fields("title")  # <2>
                        .mode(MatchMode.ALL)  # <3>
                        .build())
        results = self.repository.search(search_query)  # <4>
        first_page = self.repository.search(search_query, Pageable.from_(0, 10))  # <5>
        # end::searchQuery[]
        return [hit.entity().title for hit in results]
