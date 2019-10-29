package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Page
import io.micronaut.data.tck.entities.Shelf
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
        Shelf shelf = new Shelf(shelfName: "Some Shelf")
        def b1 = new Book(title: "The Stand", totalPages: 1000)
        b1.pages.add(new Page(num: 10))
        b1.pages.add(new Page(num: 20))
        def b2 = new Book(title: "The Shining", totalPages: 600)
        shelf.books.add(b1)
        shelf.books.add(b2)

        when:
        shelf = shelfRepository.save(shelf)

        then:
        b1.pages.every { it.id != null }
        shelf.books.every { it.id != null }

        when:
        shelf = shelfRepository.findById(shelf.id).orElse(null)

        then:
        shelf != null
        shelf.shelfName == 'Some Shelf'
        // left join causes single result since each
        // book only has a single page
        shelf.books.size() == 1
    }

}

