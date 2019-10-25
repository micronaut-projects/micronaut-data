package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.repositories.BookPageRepository
import io.micronaut.data.tck.repositories.PageRepository
import io.micronaut.data.tck.repositories.ShelfBookRepository
import io.micronaut.data.tck.repositories.ShelfRepository
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class H2UnidirectionalToManyJoinSpec extends Specification {

    @Inject
    H2ShelfRepository shelfRepository
    @Inject
    H2BookRepository bookRepository
    @Inject
    H2PageRepository pageRepository
    @Inject
    H2ShelfBookRepository shelfBookRepository
    @Inject
    H2BookPageRepository bookPageRepository

    void "test unidirectional join"() {
        given:
        def shelf = shelfRepository.save("Some Shelf")
        def b1 = bookRepository.save(new Book(title: "The Stand", totalPages: 1000))
        def b2 = bookRepository.save(new Book(title: "The Shining", totalPages: 600))

        shelfBookRepository.save(shelf, b1)
        shelfBookRepository.save(shelf, b2)
        def p10 = pageRepository.save(10)
        def p20 = pageRepository.save(20)
        bookPageRepository.save(b1, p10)
        bookPageRepository.save(b2, p20)

        shelf = shelfRepository.findById(shelf.id).orElse(null)

        expect:
        shelf != null
        shelf.shelfName == 'Some Shelf'
        // left join causes single result since each
        // book only has a single page
        shelf.books.size() == 1
    }

}

