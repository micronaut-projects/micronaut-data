package example

import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class PagedBookRepositorySpec extends Specification {

    @Inject
    PagedBookRepository bookRepository

    void setup() {
        bookRepository.saveAll([
                new Book("The Stand", 1000),
                new Book("The Shining", 600),
                new Book("The Power of the Dog", 500),
                new Book("The Border", 700),
                new Book("Along Came a Spider", 300),
                new Book("Pet Cemetery", 400),
                new Book("A Game of Thrones", 900),
                new Book("A Clash of Kings", 1100)
        ])
    }

    void cleanup() {
        bookRepository.deleteAll()
    }

    void 'test explicit query pagination'() {
        when:
        // tag::explicit-usage[]
        Pageable pageable = Pageable.from(1, 2, Sort.of(Sort.Order.desc("pages"))) // <1>
        Page<Book> page = bookRepository.findLongBooks(500, pageable) // <2>
        Slice<Book> slice = bookRepository.sliceLongBooks(500, pageable) // <3>
        List<Book> list = bookRepository.listLongBooks(500, Pageable.from(0, 3)) // <4>
        List<Book> sorted = bookRepository.listLongBooksSorted(500, Sort.of(Sort.Order.asc("title"))) // <5>
        // end::explicit-usage[]

        then:
        page.totalSize == 5
        page.totalPages == 3
        page.content*.title == ["A Game of Thrones", "The Border"]
        slice.content*.title == ["A Game of Thrones", "The Border"]
        list.size() == 3
        sorted*.title == ["A Clash of Kings", "A Game of Thrones", "The Border", "The Shining", "The Stand"]
    }

    void 'test native query pagination'() {
        when:
        // tag::native-usage[]
        Page<Book> page = bookRepository.searchByTitle("The%",
                Pageable.from(0, 3, Sort.of(Sort.Order.asc("b.pages")))) // <1>
        // end::native-usage[]

        then:
        page.totalSize == 4
        page.totalPages == 2
        page.hasNext()
        page.content*.title == ["The Power of the Dog", "The Shining", "The Border"]

        when:
        Page<Book> last = bookRepository.searchByTitle("The%", page.nextPageable())

        then:
        last.content*.title == ["The Stand"]
        !last.hasNext()
    }
}
