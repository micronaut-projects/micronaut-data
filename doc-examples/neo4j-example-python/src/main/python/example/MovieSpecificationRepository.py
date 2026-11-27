from micronaut.data.neo4j.annotation import Neo4jRepository
from micronaut.data.repository import CrudRepository
from micronaut.data.repository.jpa.criteria import PredicateSpecification

from example.Movie import Movie


# tag::repository[]
@Neo4jRepository
class MovieSpecificationRepository(CrudRepository[Movie, str]):

    def findAll(self, spec: PredicateSpecification[Movie] | None) -> list[Movie]: ...  # <1>

    def count(self, spec: PredicateSpecification[Movie]) -> int: ...
# end::repository[]
