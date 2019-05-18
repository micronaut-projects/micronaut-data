package example

import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class BookRepositorySpec extends Specification {


    // tag::inject[]
    @Inject BookRepository bookRepository
    // end::inject[]

    void "test perform CRUD"() {
        // Create: Save a new book
        when:"A book is saved"
        // tag::save[]
        Book book = new Book(title:"The Stand", pages:1000)
        bookRepository.save(book)
        // end::save[]

        Long id = book.getId()

        then:"The book has an ID"
        id != null

        // Read: Read a book from the database
        when:"A book is retrieved by ID"
        // tag::read[]
        book = bookRepository.findById(id).orElse(null)
        // end::read[]

        then:"The book is present"
        book != null
        book.title == "The Stand"
        // Check the count
        bookRepository.count() == 1
        bookRepository.findAll().iterator().hasNext()

        when:"The book is updated"
        // Update: Update the book and save it again
        // tag::update[]
        book.title = "Changed"
        bookRepository.save(book)
        // end::update[]
        book = bookRepository.findById(id).orElse(null)

        then:"The book was updated"
        book.title == "Changed"

        // Delete: Delete the book
        when:"The book is deleted"
        // tag::delete[]
        bookRepository.deleteById(id)
        // end::delete[]

        then:"It is gone"
        bookRepository.count() == 0
    }
}
