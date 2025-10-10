package io.micronaut.data.r2dbc.h2.identity

import io.micronaut.data.r2dbc.h2.H2TestPropertyProvider
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class SameIdentityRepositorySpec extends Specification implements H2TestPropertyProvider {
    @Inject
    private MyBookRepository bookRepository

    def "test get books"() {
        when:
            def books = bookRepository.getBooks().collectList().block()
        then:
            books[0].title == "Title #1"
            books[1].title == "Title #2"
    }

    def "test get books DTO"() {
        when:
            def books = bookRepository.getBooksAsDto().collectList().block()
        then:
            books[0].title() == "Title #1"
            books[1].title() == "Title #2"
    }

    def "test get books DTO 2"() {
        when:
            def books = bookRepository.getBooksAsDto2().collectList().block()
        then:
            books[0].title() == "Title #1"
            books[1].title() == "Title #2"
    }
}
