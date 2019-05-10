package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class QuerySpec extends Specification {

    @Inject
    @Shared
    BookRepository bookRepository

    def setupSpec() {
        bookRepository.save(new Book(title: "Anonymous", pages: 400))
        // blank title
        bookRepository.save(new Book(title: "", pages: 0))
        // book without an author
        bookRepository.setupData()
    }

    void "test is null or empty"() {
        expect:
        bookRepository.findByAuthorIsNull().size() == 2
        bookRepository.findByAuthorIsNotNull().size() == 6
        bookRepository.countByTitleIsEmpty() == 1
        bookRepository.countByTitleIsNotEmpty() == 7
    }
}
