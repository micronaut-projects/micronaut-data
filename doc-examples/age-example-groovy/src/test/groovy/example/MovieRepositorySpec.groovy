package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class MovieRepositorySpec extends Specification {

    @Inject
    MovieRepository movieRepository

    void cleanup() {
        movieRepository.deleteAll()
    }

    void "save and find movies"() {
        when:
        // tag::save[]
        def lana = new Person(name: "Lana Wachowski", born: 1965)
        movieRepository.save(new Movie(title: "The Matrix", released: 1999, directors: [lana])) // <1>
        movieRepository.save(new Movie(title: "The Matrix Reloaded", released: 2003, directors: [lana]))

        def directedByLana = movieRepository.findByDirectorsName("Lana Wachowski") // <2>
        // end::save[]

        then:
        directedByLana.size() == 2
        movieRepository.findByReleasedGreaterThanOrderByReleased(2000)*.title == ["The Matrix Reloaded"]
    }
}
