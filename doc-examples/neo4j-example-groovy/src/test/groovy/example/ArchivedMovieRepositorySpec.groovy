package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class ArchivedMovieRepositorySpec extends AbstractNeo4jSpec {

    @Inject
    ArchivedMovieRepository archivedMovieRepository

    @Inject
    MovieRepository movieRepository

    @Override
    Map<String, String> getProperties() {
        // Both drivers connect to the same server in the test
        ['neo4j.uri': EmbeddedNeo4j.boltUri(), 'micronaut.data.neo4j.drivers.archive.uri': EmbeddedNeo4j.boltUri()]
    }

    void cleanup() {
        movieRepository.deleteAll()
    }

    void "save through the archive driver"() {
        when:
        archivedMovieRepository.save(new Movie(title: "Metropolis", description: "The mediator between head and hands must be the heart", released: 1927))

        then:
        movieRepository.existsById("Metropolis")
    }
}
