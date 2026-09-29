package example

import io.micronaut.core.type.Argument
import io.micronaut.data.model.Page
import io.micronaut.data.model.Sort
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class BookControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

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

    void 'test pageable binding'() {
        when:
        // tag::request[]
        Page<BookSummary> page = client.toBlocking().retrieve(
                HttpRequest.GET("/books?page=1&size=3&sort=pages,desc&sort=title"), // <1>
                Argument.of(Page, BookSummary) // <2>
        )
        // end::request[]

        then:
        page.totalSize == 8
        page.totalPages == 3
        page.pageNumber == 1
        page.size == 3
        page.pageable.orderBy == [Sort.Order.desc("pages"), Sort.Order.asc("title")]
        page.content*.title == ["The Border", "The Shining", "The Power of the Dog"]
    }

    void 'test defaults'() {
        when: 'no parameters are supplied'
        Page<BookSummary> page = client.toBlocking().retrieve(
                HttpRequest.GET("/books"),
                Argument.of(Page, BookSummary)
        )

        then: 'the first page, the default page size (the max page size: 100) and no sorting'
        page.pageNumber == 0
        page.size == 100
        page.content.size() == 8
        page.pageable.orderBy.isEmpty()
    }
}
