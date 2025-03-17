package io.micronaut.data.jdbc.h2.joinissue

import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@H2DBProperties
class DirectorSpec extends Specification {

    @Inject
    DirectorRepository directorRepository

    def 'test'() {
        given:
            var directorList = List.of(
                    new Director("John Jones",
                            Set.of(new Movie("Random Movie"))),
                    new Director("Ann Jones",
                            Set.of(new Movie("Super Hero Movie"),
                                    new Movie("Anther Movie with Heroes"))))

            directorRepository.saveAll(directorList)

        when:
            var director = directorRepository.queryByName("John Jones").orElse(null)
        then:
            director.getName() == "John Jones"
            director.getMovies().size() == 1

        when:
            var list = directorRepository.queryByNameContains("n Jones")
        then:
            list.size() == 2
            list.get(0).getName() == "John Jones"
            list.get(0).getMovies().size() == 1
            list.get(1).getName() == "Ann Jones"
            list.get(1).getMovies().size() == 2

        when:
            director = directorRepository.findByNameContains("n Jones").orElse(null)
        then:
            director.getName() == "John Jones"
            director.getMovies().size() == 1

    }
}
