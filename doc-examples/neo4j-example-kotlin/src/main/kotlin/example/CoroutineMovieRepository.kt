package example

import io.micronaut.data.neo4j.annotation.Neo4jRepository
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import kotlinx.coroutines.flow.Flow

// tag::repository[]
@Neo4jRepository
interface CoroutineMovieRepository : CoroutineCrudRepository<Movie, String> { // <1>

    suspend fun findByTitle(title: String): Movie? // <2>

    fun findByReleasedGreaterThan(year: Int): Flow<Movie> // <3>
}
// end::repository[]
