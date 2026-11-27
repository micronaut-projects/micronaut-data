from jakarta.inject import Singleton
from micronaut.data.model import Pageable
from micronaut.data.model.search import MatchMode, SearchQuery
from micronaut.data.model.vector import Vector

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

    def hybrid_titles(self, query: str, embedding: Vector) -> list[str]:
        # tag::hybrid[]
        search_query = (SearchQuery.builder(query)
                        .vector("embedding", embedding)  # <1>
                        .textWeight(1.0).vectorWeight(1.0).candidates(50)  # <2>
                        .build())
        results = self.repository.search(search_query)
        for hit in results:
            fused_score = hit.score().value()  # <3>
        # end::hybrid[]
        return [hit.entity().title for hit in results]

    def highlighted_titles(self, query: str) -> list[str]:
        # tag::highlight[]
        search_query = (SearchQuery.builder(query)
                        .highlight("title")  # <1>
                        .build())
        fragments = []
        for hit in self.repository.search(search_query):
            fragments.extend(hit.highlights()["title"])  # <2>
        # end::highlight[]
        return fragments

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
