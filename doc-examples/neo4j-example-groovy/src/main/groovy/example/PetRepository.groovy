package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.neo4j.annotation.Neo4jRepository
import io.micronaut.data.repository.CrudRepository

@Neo4jRepository
interface PetRepository extends CrudRepository<Pet, String> {

    // tag::query[]
    @Query('MATCH (p:Pet) WHERE p.goesInPatch = $goesInPatch RETURN p')
    List<Pet> findByGoesInPatch(boolean goesInPatch)
    // end::query[]
}
