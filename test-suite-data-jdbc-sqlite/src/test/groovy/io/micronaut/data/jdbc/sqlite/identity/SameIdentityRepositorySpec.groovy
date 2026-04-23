package io.micronaut.data.jdbc.sqlite.identity

import io.micronaut.context.annotation.Property
import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.identity")
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
