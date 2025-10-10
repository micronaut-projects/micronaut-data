package io.micronaut.data.jdbc.h2.identity

import io.micronaut.context.annotation.Property
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@H2DBProperties
@Property(name = "datasources.default.packages", value = "io.micronaut.data.jdbc.h2.identity")
@Property(name = "datasources.default.batch-generate", value = "true")
@MicronautTest
class SameIdentityRepositorySpec extends Specification {
    @Inject
    private MyBookRepository bookRepository

    def "test get books"() {
        when:
            def books = bookRepository.getBooks()
        then:
            books[0].title == "Title #1"
            books[1].title == "Title #2"
    }

    def "test get books DTO"() {
        when:
            def books = bookRepository.getBooksAsDto()
        then:
            books[0].title() == "Title #1"
            books[1].title() == "Title #2"
    }

    def "test get books DTO 2"() {
        when:
            def books = bookRepository.getBooksAsDto2()
        then:
            books[0].title() == "Title #1"
            books[1].title() == "Title #2"
    }
}
