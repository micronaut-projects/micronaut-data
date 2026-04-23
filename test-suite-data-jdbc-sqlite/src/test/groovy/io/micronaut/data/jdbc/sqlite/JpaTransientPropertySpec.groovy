package io.micronaut.data.jdbc.sqlite

import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.tck.entities.Book
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@SQLiteDBProperties
class JpaTransientPropertySpec  extends Specification {

    @Inject
    SQLiteBookRepository bookRepository

    void "test JpaSpecificationExecutor with transient properties"() {
        given: 'a PredicateSpecification'
        PredicateSpecification<Book> spec = (root, criteriaBuilder) -> criteriaBuilder.equal(root.get("title"), "Random title")

        when: 'a book is searched using JpaSpecificationExecutor'
        bookRepository.findAll(spec)

        then:
        noExceptionThrown()
    }
}
