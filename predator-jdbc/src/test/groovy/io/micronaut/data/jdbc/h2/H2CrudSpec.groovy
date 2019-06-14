package io.micronaut.data.jdbc.h2


import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Book
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import javax.sql.DataSource

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
class H2CrudSpec extends Specification {

    @Inject
    @Shared
    BookRepository bookRepository

    @Inject
    @Shared
    DataSource dataSource


    void setupSpec() {
        H2Util.createTables(dataSource, Book)
    }


    void "test CRUD with JDBC"() {
        when:"we save a new book"
        def book = bookRepository.save(new Book(title: "The stand", pages: 1000))

        then:"The ID is assigned"
        book.id != null

        when:"The book is retrieved again"
        book = bookRepository.findById(book.id).orElse(null)

        then:"The book is back and valid"
        book.id != null
        book.title == "The stand"
        book.pages == 1000


        and:"The results are correct"
        def results = bookRepository.findAll()
        results.size() == 1
        results[0].title == book.title
        results[0].id
        bookRepository.count() == 1
    }
}
