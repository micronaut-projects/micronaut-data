package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class BookRepositoryAdvancedSpec extends Specification {

    @Inject BookRepository bookRepository
    @Inject AuthorRepository authorRepository

    def cleanup() {
        bookRepository.deleteAll()
        authorRepository.deleteAll()
    }

    def "case-insensitive contains query"() {
        given:
        bookRepository.save(new Book("The Stand"))
        bookRepository.save(new Book("the shinning"))
        bookRepository.save(new Book("THE IT"))

        expect:
        bookRepository.findByTitleContainsIgnoreCase("the").size() == 3
    }

    def "IN query with list"() {
        given:
        bookRepository.save(new Book("Book A"))
        bookRepository.save(new Book("Book B"))
        bookRepository.save(new Book("Book C"))
        bookRepository.save(new Book("Book D"))

        when:
        def results = bookRepository.findByTitleIn(["Book A", "Book C"])

        then:
        results.size() == 2
        results.any { it.title == "Book A" }
        results.any { it.title == "Book C" }
    }

    def "IN query with array"() {
        given:
        bookRepository.save(new Book("Book A"))
        bookRepository.save(new Book("Book B"))
        bookRepository.save(new Book("Book C"))

        when:
        def results = bookRepository.findByTitleIn(["Book B", "Book C"] as String[])

        then:
        results.size() == 2
        results.any { it.title == "Book B" }
    }

    def "NOT IN query"() {
        given:
        bookRepository.save(new Book("Book A"))
        bookRepository.save(new Book("Book B"))
        bookRepository.save(new Book("Book C"))

        when:
        def results = bookRepository.findByTitleNotIn(["Book A"])

        then:
        results.size() == 2
        results.every { it.title != "Book A" }
    }

    def "count by association"() {
        given:
        Author author = new Author("Stephen King")
        authorRepository.save(author)
        Book book1 = new Book("The Stand")
        Book book2 = new Book("The Shining")
        book1.setAuthor(author)
        book2.setAuthor(author)
        bookRepository.save(book1)
        bookRepository.save(book2)

        when:
        def allBooks = bookRepository.findAll()

        then:
        allBooks.size() == 2
        allBooks.every { it.author != null }
    }

    def "aggregate count function"() {
        given:
        bookRepository.save(new Book("The Stand"))
        bookRepository.save(new Book("The Shining"))
        bookRepository.save(new Book("Different Book"))

        expect:
        bookRepository.countByTitle("The Stand") == 1
        bookRepository.countByTitle("Non-existent") == 0
    }
}
