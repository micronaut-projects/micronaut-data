package example

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = 'spec.name', value = 'BookRepositorySpec')
@Property(name = 'datasources.default.name', value = 'mydb')
@Property(name = 'datasources.default.transactionManager', value = 'springJdbc')
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@MicronautTest(transactional = false)
class BookRepositorySpec extends Specification {

    @Inject
    AbstractBookRepository abstractBookRepository

    void "test Books JdbcTemplate"() {
        given:
        abstractBookRepository.saveAll(Arrays.asList(
                new Book(null,'The Stand', 1000),
                new Book(null,'The Shining', 600),
                new Book(null,'The Power of the Dog', 500),
                new Book(null,'The Border', 700),
                new Book(null,'Along Came a Spider', 300),
                new Book(null,'Pet Cemetery', 400),
                new Book(null,'A Game of Thrones', 900),
                new Book(null,'A Clash of Kings', 1100)
        ))

        when:
        List<Book> result = abstractBookRepository.findByTitle('The Shining')

        then:
        result.size() == 1

        cleanup:
        abstractBookRepository.deleteAll()
    }
}
