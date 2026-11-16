from micronaut.data.annotation import Query
from micronaut.data.neo4j.annotation import Neo4jRepository
from micronaut.data.repository import CrudRepository

from example.Pet import Pet


@Neo4jRepository
class PetRepository(CrudRepository[Pet, str]):

    # tag::query[]
    @Query("MATCH (p:Pet) WHERE p.goesInPatch = $goesInPatch RETURN p")
    def findByGoesInPatch(self, goesInPatch: bool) -> list[Pet]: ...
    # end::query[]
