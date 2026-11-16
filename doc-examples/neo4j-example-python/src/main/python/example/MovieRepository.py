# tag::repository[]
from micronaut.data.annotation import Join, Query
from micronaut.data.model import Page, Pageable
from micronaut.data.neo4j.annotation import Neo4jRepository
from micronaut.data.repository import CrudRepository

from example.Movie import Movie
from example.MovieSummary import MovieSummary


@Neo4jRepository  # <1>
class MovieRepository(CrudRepository[Movie, str]):  # <2>
    # end::repository[]

    # tag::finders[]
    def findByReleasedGreaterThanOrderByReleased(self, year: int) -> list[Movie]: ...

    def findByTitleContainsIgnoreCase(self, text: str) -> list[Movie]: ...

    def countByReleasedLessThan(self, year: int) -> int: ...

    def findByReleasedGreaterThan(self, year: int, pageable: Pageable) -> Page[Movie]: ...
    # end::finders[]

    # tag::traversal[]
    def findByDirectorsName(self, name: str) -> list[Movie]: ...  # <1>

    def findByRolesPersonName(self, name: str) -> list[Movie]: ...  # <2>
    # end::traversal[]

    # tag::join[]
    @Join("roles")  # <1>
    @Join("directors")
    def findByTitle(self, title: str) -> Movie | None: ...
    # end::join[]

    # tag::projection[]
    def findTitleByReleasedGreaterThanOrderByTitle(self, year: int) -> list[str]: ...

    def findByReleasedBetween(self, start: int, end: int) -> list[MovieSummary]: ...
    # end::projection[]

    # tag::query[]
    @Query("MATCH (m:Movie)<-[:ACTED_IN]-(p:Person) WHERE p.name = $name RETURN m ORDER BY m.released")
    def findMoviesActedInBy(self, name: str) -> list[Movie]: ...

    @Query("MATCH (m:Movie {title: $title}) SET m.votes = coalesce(m.votes, 0) + 1")
    def vote(self, title: str) -> None: ...
    # end::query[]
