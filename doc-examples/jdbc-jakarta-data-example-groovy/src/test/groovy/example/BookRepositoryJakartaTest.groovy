package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.data.Sort
import jakarta.data.page.CursoredPage
import jakarta.data.page.Page
import jakarta.data.page.PageRequest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class BookRepositoryJakartaTest extends Specification {

    @Inject
    BookRepository bookRepository

    void setup() {
        bookRepository.saveAll([
                new Book(title: "The Stand", pages: 1000),
                new Book(title: "The Shining", pages: 600),
                new Book(title: "The Power of the Dog", pages: 500),
                new Book(title: "The Border", pages: 700),
                new Book(title: "Along Came a Spider", pages: 300),
                new Book(title: "Pet Cemetery", pages: 400),
                new Book(title: "A Game of Thrones", pages: 900),
                new Book(title: "A Clash of Kings", pages: 1100)
        ])
    }

    void cleanup() {
        bookRepository.deleteAll()
    }

    void "test CRUD"() {
        when:
        Book book = bookRepository.insert(new Book(title: "Carrie", pages: 200))

        then:
        book.id != null
        bookRepository.findById(book.id).orElse(null)?.title == "Carrie"
        bookRepository.count() == 9
    }

    void "test pageable"() {
        when:
        // tag::pageable[]
        Page<Book> slice = bookRepository.list(PageRequest.ofSize(3))
        List<Book> resultList =
                bookRepository.findByPagesGreaterThan(500, PageRequest.ofSize(3))
        Page<Book> page = bookRepository.findByTitleLike("The%", PageRequest.ofSize(3))
        // end::pageable[]

        then:
        slice.numberOfElements() == 3
        resultList.size() == 3
        page.numberOfElements() == 3
        page.totalPages() == 2
    }

    void "test cursored pageable"() {
        when:
        // tag::cursored-pageable[]
        Sort<Object> order = Sort.asc("title")
        CursoredPage<Book> page = // <1>
                bookRepository.find(PageRequest.ofSize(5), order)
        CursoredPage<Book> page2 = bookRepository.find(page.nextPageRequest(), order) // <2>
        CursoredPage<Book> pageByPagesBetween = // <3>
                bookRepository.findByPagesBetween(400, 700, PageRequest.ofSize(3))
        Page<Book> pageByTitleStarts = // <4>
                bookRepository.findByTitleStartingWith("The", PageRequest.ofSize(3))
        // end::cursored-pageable[]

        then:
        page.numberOfElements() == 5
        page2.numberOfElements() == 3
        pageByPagesBetween.numberOfElements() == 3
        pageByTitleStarts.numberOfElements() == 3
    }
}
