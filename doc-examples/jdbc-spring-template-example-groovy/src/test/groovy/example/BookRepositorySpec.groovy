package example

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
@Property(name = 'spec.name', value = 'BookRepositorySpec')
@Property(name = 'datasources.default.name', value = 'mydb')
@Property(name = 'datasources.default.transaction-manager', value = 'springJdbc')
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class BookRepositorySpec extends Specification {

    @Inject
    AbstractBookRepository bookRepository

    void "test Books JdbcTemplate"() {
        given:
        bookRepository.saveAll([
                new Book(null, 'The Stand', 1000),
                new Book(null, 'The Shining', 600),
                new Book(null, 'The Power of the Dog', 500),
                new Book(null, 'The Border', 700),
                new Book(null, 'Along Came a Spider', 300),
                new Book(null, 'Pet Cemetery', 400),
                new Book(null, 'A Game of Thrones', 900),
                new Book(null, 'A Clash of Kings', 1100)
        ])

        when:
        List<Book> result = bookRepository.findByTitle('The Shining')

        then:
        result.size() == 1

        result[0].id != null
        result[0].title == 'The Shining'
        result[0].pages == 600

        cleanup:
        bookRepository.deleteAll()
    }
}
