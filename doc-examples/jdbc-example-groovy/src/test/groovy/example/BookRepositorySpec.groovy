package example

import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

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


}
