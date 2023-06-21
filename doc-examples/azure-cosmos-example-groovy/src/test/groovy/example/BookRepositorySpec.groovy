package example

import io.micronaut.context.BeanContext
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.Pageable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.IgnoreIf
import spock.lang.Shared

@MicronautTest
@IgnoreIf({ env["GITHUB_WORKFLOW"] })
class BookRepositorySpec extends AbstractAzureCosmosSpec {

    // tag::inject[]
    @Inject @Shared BookRepository bookRepository
    // end::inject[]

    // tag::metadata[]
    @Inject @Shared BeanContext beanContext

    void cleanup() {
        bookRepository.deleteAll()
    }

    void 'test annotation metadata'() {
        when:
            def query = beanContext.getBeanDefinition(BookRepository.class) // <1>
                .getRequiredMethod("find", String.class) // <2>
                .getAnnotationMetadata()
                .stringValue(Query.class) // <3>
                .orElse(null)
        then:
            query == "SELECT DISTINCT VALUE book_ FROM book book_ WHERE (book_.title = @p1)" // <4>
    }
    // end::metadata[]

    void 'test Crud'() {
        when:
            // Create: Save a new book
            // tag::save[]
            def book = new Book("The Stand", 1000)
            book.itemPrice = new ItemPrice(99.5)
            bookRepository.save(book)
            def id = book.id
            // end::save[]
        then:
            id
        when:
            // Read: Read a book from the database
            // tag::read[]
            book = bookRepository.findById(id).orElse(null)
            // end::read[]
        then:
            book
            book.title == "The Stand"
            book.itemPrice
            book.itemPrice.price == 99.5
        when:
            def count = bookRepository.count()
        then:
            // Check the count
            count == 1
        when:
            def iterator = bookRepository.findAll().iterator()
        then:
            iterator.hasNext()
        when:
            // Update: Update the book and save it again
            // tag::update[]
            bookRepository.update(book.getId(), "Changed")
            // end::update[]
            book = bookRepository.findById(id).orElse(null)
        then:
            book.title == "Changed"
        when:
            // Delete: Delete the book
            // tag::delete[]
            bookRepository.deleteById(id)
            // end::delete[]
            count = bookRepository.count()
        then:
            count == 0
    }

    void 'test pageable'() {
        given:
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
        when:
        // tag::pageable[]
            def slice = bookRepository.list(Pageable.from(0, 3))
            def resultList = bookRepository.findByPagesGreaterThan(500, Pageable.from(0, 3))
        // end::pageable[]
        then:
            slice.numberOfElements == 3
            resultList.size() == 3
    }

    void 'test Dto'() {
        when:
            bookRepository.save(new Book("The Shining", 400))
            def bookDto = bookRepository.findOne("The Shining")
        then:
            bookDto.title == "The Shining"
    }
}
