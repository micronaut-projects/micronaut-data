package example

import io.micronaut.context.BeanContext
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
class BookRepositorySpec extends Specification {


    // tag::inject[]
    @Inject BookRepository bookRepository
    // end::inject[]

    // tag::metadata[]
    @Inject
    BeanContext beanContext

    void "test annotation metadata"() {
        given:"The value of the Query annotation"
        String query = beanContext.getBeanDefinition(BookRepository.class) // <1>
                .getRequiredMethod("find", String.class) // <2>
                .getAnnotationMetadata()
                .stringValue(Query.class) // <3>
                .orElse(null)

        expect:"The JPA-QL query to be correct" // <4>
        query == "SELECT book_ FROM example.Book AS book_ WHERE (book_.title = :p1)"
    }
    // end::metadata[]

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

        when:"The book is partially updated"
        bookRepository.updatePages(id, 1200)
        book = bookRepository.findById(id).orElse(null)

        then:
        book.pages == 1200

        // Delete: Delete the book
        when:"The book is deleted"
        // tag::delete[]
        bookRepository.deleteById(id)
        // end::delete[]

        then:"It is gone"
        bookRepository.count() == 0
    }

    void "test pageable"() {
        given:"Some test data"
        // tag::saveall[]
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
        // end::saveall[]

        // tag::pageable[]
        Slice<Book> slice = bookRepository.list(Pageable.from(0, 3))
        List<Book> resultList =
                bookRepository.findByPagesGreaterThan(500, Pageable.from(0, 3))
        Page<Book> page = bookRepository.findByTitleLike("The%", Pageable.from(0, 3))
        // end::pageable[]

        expect:
        slice.getNumberOfElements() == 3
        resultList.size() == 3
        page.getNumberOfElements() == 3
        page.getTotalSize() == 4
    }

    void "test DTO"() {
        when:"A DTO object is queried for"
        bookRepository.save(new Book("The Shining", 400))
        BookDTO book = bookRepository.findOne("The Shining")

        then:"The result is correct"
        book.title == "The Shining"
    }
}
