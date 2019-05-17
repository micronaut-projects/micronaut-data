package example

import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class BookRepositorySpec extends Specification {


    @Inject BookRepository bookRepository

    void "test perform CRUD"() {
        // Create: Save a new book
        when:"A book is saved"
        Book book = new Book()
        book.setTitle("The Stand")
        book.setPages(1000)
        bookRepository.save(book)

        Long id = book.getId()

        then:"The book has an ID"
        id != null

        // Read: Read a book from the database
        when:"A book is retrieved by ID"
        book = bookRepository.findById(id).orElse(null)

        then:"The book is present"
        book != null
        book.title == "The Stand"
        // Check the count
        bookRepository.count() == 1
        bookRepository.findAll().iterator().hasNext()

        when:"The book is updated"
        // Update: Update the book and save it again
        book.setTitle("Changed")
        bookRepository.save(book)
        book = bookRepository.findById(id).orElse(null)

        then:"The book was updated"
        book.title == "Changed"

        // Delete: Delete the book
        when:"The book is deleted"
        bookRepository.deleteById(id)

        then:"It is gone"
        bookRepository.count() == 0
    }
}
