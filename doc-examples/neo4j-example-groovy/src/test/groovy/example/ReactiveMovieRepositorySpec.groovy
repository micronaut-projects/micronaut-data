package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class ReactiveMovieRepositorySpec extends AbstractNeo4jSpec {

    @Inject
    ReactiveMovieRepository movieRepository

    void cleanup() {
        movieRepository.deleteAll().block()
    }

    void "reactive repositories"() {
        when:
        // tag::reactive[]
        def titles = movieRepository.saveAll([ // <1>
                new Movie(title: "The Matrix", description: "Welcome to the Real World", released: 1999),
                new Movie(title: "Speed", description: "Get ready for rush hour", released: 1994)
            ])
            .thenMany(movieRepository.findByReleasedGreaterThan(1995)) // <2>
            .map { it.title }
            .collectList()
            .block() // <3>
        // end::reactive[]

        then:
        titles == ["The Matrix"]
    }
}
