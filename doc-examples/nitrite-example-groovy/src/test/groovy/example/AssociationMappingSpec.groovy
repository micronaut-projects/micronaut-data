package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class AssociationMappingSpec extends Specification {

    @Inject AuthorRepository authorRepository
    @Inject BookRepository bookRepository

    def cleanup() {
        authorRepository.deleteAll()
        bookRepository.deleteAll()
    }

    def "one-to-many with cascade persist"() {
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

    def "many-to-one association"() {
        given:
        Author author = new Author("Stephen King")
        authorRepository.save(author)
        Book book = new Book("The Stand")
        book.setAuthor(author)
        bookRepository.save(book)

        when:
        def saved = bookRepository.findById(book.id).orElse(null)

        then:
        saved != null
        saved.title == "The Stand"
        saved.author != null
        saved.author.name == "Stephen King"
    }

    def "embedded address fields are preserved"() {
        given:
        Address address = new Address("123 Main St", "New York", "10001")

        expect:
        address.street == "123 Main St"
        address.city == "New York"
        address.zipCode == "10001"
    }

    def "bidirectional association is persisted"() {
        given:
        Author author = new Author("Stephen King")
        Book book = new Book("The Stand")
        author.getBooks().add(book)
        book.setAuthor(author)

        when:
        authorRepository.save(author)

        then:
        authorRepository.findById(author.id).get().books.size() == 1
        bookRepository.findById(book.id).get().author.name == "Stephen King"
    }

    def "join fetch returns books for author"() {
        given:
        Author author = new Author("Stephen King")
        Book book1 = new Book("The Stand")
        Book book2 = new Book("The Shining")
        book1.setAuthor(author)
        book2.setAuthor(author)
        author.getBooks().add(book1)
        author.getBooks().add(book2)
        authorRepository.save(author)

        when:
        def found = authorRepository.searchByName("Stephen King")

        then:
        found != null
        found.books.size() == 2
        found.books*.title.contains("The Stand")
        found.books*.title.contains("The Shining")
    }

    def "reverse lookup finds author with books"() {
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
