package example

import io.micronaut.data.annotation.Join
import io.micronaut.data.neo4j.annotation.Neo4jRepository
import io.micronaut.data.repository.CrudRepository

@Neo4jRepository
interface MovieRepository : CrudRepository<Movie, String> {

    fun findByReleasedGreaterThan(released: Int): List<Movie>

    @Join("directors")
    fun findByDirectorsName(name: String): List<Movie>
}
