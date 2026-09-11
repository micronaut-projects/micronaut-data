package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class AuthorRepositorySpec extends Specification {

    @Inject AuthorRepository authorRepository
    @Inject BookRepository bookRepository

    def cleanup() {
        authorRepository.deleteAll()
        bookRepository.deleteAll()
    }

    def "cascade persist saves books with author"() {
        given:
        Author author = new Author("Stephen King")
        Book book1 = new Book("The Stand")
        Book book2 = new Book("The Shining")
        book1.setAuthor(author)
        book2.setAuthor(author)
        author.getBooks().add(book1)
        author.getBooks().add(book2)

        when:
        authorRepository.save(author)

        then:
        author.id != null
        book1.id != null
        book2.id != null
        authorRepository.findById(author.id).get().books.size() == 2
    }

    def "find with @Join eagerly fetches books"() {
        given:
        Author author = new Author("Stephen King")
        Book book = new Book("The Stand")
        book.setAuthor(author)
        author.getBooks().add(book)
        authorRepository.save(author)

        when:
        def found = authorRepository.findById(author.id).orElse(null)

        then:
        found != null
        found.books.size() == 1
        found.books.iterator().next().title == "The Stand"
    }

    def "search by name with @Join"() {
        given:
        Author author = new Author("Stephen King")
        Book book = new Book("The Stand")
        book.setAuthor(author)
        author.getBooks().add(book)
        authorRepository.save(author)

        when:
        def found = authorRepository.searchByName("Stephen King")

        then:
        found != null
        found.books.size() == 1
    }

    def "reverse lookup includes books"() {
        given:
        Author author = new Author("Stephen King")
        Book book = new Book("The Stand")
        book.setAuthor(author)
        author.getBooks().add(book)
        authorRepository.save(author)

        when:
        def found = authorRepository.findById(author.id).orElse(null)

        then:
        found != null
        found.name == "Stephen King"
        found.books.size() == 1
    }
}
