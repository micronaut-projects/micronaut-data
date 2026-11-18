from jakarta.inject import Singleton

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
