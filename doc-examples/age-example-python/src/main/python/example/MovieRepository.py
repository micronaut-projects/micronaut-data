# tag::repository[]
from micronaut.data.age.annotation import AgeRepository
from micronaut.data.repository import CrudRepository

from example.Movie import Movie


@AgeRepository  # <1>
class MovieRepository(CrudRepository[Movie, str]):  # <2>

    def findByDirectorsName(self, name: str) -> list[Movie]: ...  # <3>

    def findByReleasedGreaterThanOrderByReleased(self, year: int) -> list[Movie]: ...
# end::repository[]
