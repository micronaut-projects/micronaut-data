# tag::repository[]
from micronaut.data.age.r2dbc.annotation import AgeR2dbcRepository
from micronaut.data.repository.reactive import ReactorCrudRepository
from reactor.core.publisher import Flux

from example.Movie import Movie


@AgeR2dbcRepository  # <1>
class MovieRepository(ReactorCrudRepository[Movie, str]):  # <2>

    def findByDirectorsName(self, name: str) -> Flux[Movie]: ...  # <3>

    def findByReleasedGreaterThanOrderByReleased(self, year: int) -> Flux[Movie]: ...
# end::repository[]
