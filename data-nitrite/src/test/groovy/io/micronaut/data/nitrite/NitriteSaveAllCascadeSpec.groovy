package io.micronaut.data.nitrite

import io.micronaut.data.document.tck.entities.Author
import io.micronaut.data.document.tck.entities.AuthorBooksDto
import io.micronaut.data.document.tck.entities.Book
import io.micronaut.data.document.tck.entities.BookDto
import io.micronaut.data.nitrite.tck.NitriteAuthorRepository
import io.micronaut.data.nitrite.tck.NitriteBookRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteSaveAllCascadeSpec extends Specification {

    @Inject
    NitriteAuthorRepository authorRepository

    @Inject
    NitriteBookRepository bookRepository

    def setup() {
        // Clear all data before each test
        authorRepository.findAll().each { authorRepository.delete(it) }
        bookRepository.findAll().each { bookRepository.delete(it) }
    }

    void "test saveAll with existing IDs (upsert)"() {
        given:
            def author1 = new Author(name: "Author 1")
            def author2 = new Author(name: "Author 2")
            authorRepository.saveAll([author1, author2])

        when: "modifying entities that already have IDs and saving them again via saveAll"
            author1.name = "Author 1 Updated"
            author2.name = "Author 2 Updated"
            authorRepository.saveAll([author1, author2])

        then: "upserts (updates) the existing rows instead of creating new ones"
            authorRepository.count() == 2
            def names = authorRepository.findAll().toList()*.name.sort()
            names == ["Author 1 Updated", "Author 2 Updated"]
    }
}
