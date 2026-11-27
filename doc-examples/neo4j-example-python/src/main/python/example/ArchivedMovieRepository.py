# tag::repository[]
from micronaut.data.neo4j.annotation import Neo4jRepository
from micronaut.data.repository import CrudRepository

from example.Movie import Movie


@Neo4jRepository("archive")  # <1>
class ArchivedMovieRepository(CrudRepository[Movie, str]):
    pass
# end::repository[]
