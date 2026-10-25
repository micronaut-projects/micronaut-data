package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class MovieRepositorySpec extends Specification {

    @Inject
    MovieRepository movieRepository

    void cleanup() {
        movieRepository.deleteAll().block()
    }

    void "save and find movies reactively"() {
        when:
        // tag::reactive[]
        def lana = new Person(name: "Lana Wachowski", born: 1965)
        def directedByLana = movieRepository.saveAll([ // <1>
                new Movie(title: "The Matrix", released: 1999, directors: [lana]),
                new Movie(title: "The Matrix Reloaded", released: 2003, directors: [lana])
            ])
            .thenMany(movieRepository.findByDirectorsName("Lana Wachowski")) // <2>
            .collectList()
            .block() // <3>
        // end::reactive[]

        then:
        directedByLana.size() == 2
        movieRepository.findByReleasedGreaterThanOrderByReleased(2000).collectList().block()*.title == ["The Matrix Reloaded"]
    }
}
