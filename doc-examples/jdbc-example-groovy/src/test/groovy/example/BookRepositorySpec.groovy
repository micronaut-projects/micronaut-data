package example

import io.micronaut.data.model.CursoredPage
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
class BookRepositorySpec extends Specification {

    // tag::inject[]
    @Inject @Shared BookRepository bookRepository
    // end::inject[]

    void 'test CRUD operations'() {

        when: "Create: Save a new book"
        // tag::save[]
        Book book = new Book("The Stand", 1000)
        bookRepository.save(book)
        // end::save[]
        Long id = book.id
        then: "An ID was assigned"
        id != null

        when: "Read a book from the database"
        // tag::read[]
        book = bookRepository.findById(id).orElse(null)
        // end::read[]

        then:"The book was read"
        book != null
        book.title == 'The Stand'

        // Check the count
        bookRepository.count() == 1
        bookRepository.findAll().iterator().hasNext()

        when: "The book is updated"
        // tag::update[]
        bookRepository.update(book.getId(), "Changed")
        // end::update[]
        book = bookRepository.findById(id).orElse(null)
        then: "The title was changed"
        book.title == 'Changed'

        when: "The book is deleted"
        // tag::delete[]
        bookRepository.deleteById(id)
        // end::delete[]
        then:"It is gone"
        bookRepository.count() == 0
    }

    void "test cursored pageable"() {
        given:
        bookRepository.saveAll(Arrays.asList(
                new Book("The Stand", 1000),
                new Book("The Shining", 600),
                new Book("The Power of the Dog", 500),
                new Book("The Border", 700),
                new Book("Along Came a Spider", 300),
                new Book("Pet Cemetery", 400),
                new Book("A Game of Thrones", 900),
                new Book("A Clash of Kings", 1100)
        ))

        when:
        // tag::cursored-pageable[]
        CursoredPage<Book> page =  // <1>
                bookRepository.find(CursoredPageable.from(5, Sort.of(Sort.Order.asc("title"))))
        CursoredPage<Book> page2 = bookRepository.find(page.nextPageable()) // <2>
        CursoredPage<Book> pageByPagesBetween = // <3>
                bookRepository.findByPagesBetween(400, 700, Pageable.from(0, 3))
        Page<Book> pageByTitleStarts = // <4>
                bookRepository.findByTitleStartingWith("The", CursoredPageable.from( 3, Sort.unsorted()))
        // end::cursored-pageable[]

        then:
        page.getNumberOfElements() == 5
        page2.getNumberOfElements() == 3
        pageByPagesBetween.getNumberOfElements() == 3
        pageByTitleStarts.getNumberOfElements() == 3
    }

    void "test one-to-many custom query"() {
        when:
        bookRepository.save(new Book("Dummy Book", 20, Set.of(new Review(reviewer: "Anonymous", content: "Lorem Ipsum"),
                new Review(reviewer: "Library Member", content: "Interesting"))))
        def books = bookRepository.searchBooksByTitle("Dummy Book")
        then:
        books.size() == 1
        def book = books.get(0)
        book.getTitle() == "Dummy Book"
        book.reviews.size() == 2
    }
}
