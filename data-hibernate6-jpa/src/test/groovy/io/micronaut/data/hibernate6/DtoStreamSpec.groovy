package io.micronaut.data.hibernate6

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.AuthorBooksDto
import io.micronaut.data.tck.entities.BookDto
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest(rollback = false, transactional = true, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class DtoStreamSpec extends Specification {

    @Inject
    @Shared
    BookRepository bookRepository

    @Inject
    @Shared
    BookDtoRepository bookDtoRepository

    def setup() {
        bookRepository.saveAuthorBooks([
                new AuthorBooksDto("Stephen King", Arrays.asList(
                        new BookDto("The Stand", 1000),
                        new BookDto("Pet Cemetery", 400)
                )),
        ])
    }

    def cleanup() {
        bookRepository.deleteAll()
    }

    void "you can return a Stream of DTOs from a repository"() {
        when: "Stream is used"
        def dto = bookDtoRepository.findStream("The Stand").findFirst().get()

        then: "The result is correct"
        dto instanceof BookDto
        dto.title == "The Stand"
    }
}
