package io.micronaut.data.jdbc.h2

import io.micronaut.data.model.Pageable
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
@H2DBProperties
class CountTest extends Specification {

    @Inject
    @Shared
    NewAuthorRepository authorRepository

    @Inject
    @Shared
    NewGenreRepository genreRepository

    void "test it"() {
        given:
        def genre1 = new NewGenre()
        genre1.id = 1L
        genre1.name = "Horror"
        def genre2 = new NewGenre()
        genre2.id = 2L
        genre2.name = "Thriller"
        def genre3 = new NewGenre()
        genre3.id = 3L
        genre3.name = "Comedy"
        genreRepository.saveAll(List.of(genre1, genre2, genre3))
        def author = new NewAuthor()
        author.id = 1L
        author.name = "Stephen King"
        author.genres.add(genre1)
        author.genres.add(genre2)
        authorRepository.save(author)
        when:
        def page = authorRepository.findAll(QuerySpecification.where(NewAuthorRepository.Specifications.criteria()),
                Pageable.from(0, 10))
        then:
        page.totalSize == page.content.size()
    }
}
