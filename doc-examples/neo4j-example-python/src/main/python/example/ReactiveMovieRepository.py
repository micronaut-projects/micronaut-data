# tag::repository[]
from micronaut.data.neo4j.annotation import Neo4jRepository
from micronaut.data.repository.reactive import ReactorCrudRepository
from reactor.core.publisher import Flux

from example.Movie import Movie


@Neo4jRepository
class ReactiveMovieRepository(ReactorCrudRepository[Movie, str]):  # <1>

    def findByReleasedGreaterThan(self, year: int) -> Flux[Movie]: ...  # <2>
# end::repository[]
