package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@MicronautTest
@JavaSQLiteDBProperties
class JpaTransientPropertyTest {

    @Inject
    SQLiteBookRepository bookRepository;

    @Test
    void testJpaSpecificationExecutorWithTransientProperties() {
        PredicateSpecification<Book> spec = (root, criteriaBuilder) -> criteriaBuilder.equal(root.get("title"), "Random title");
        assertDoesNotThrow(() -> bookRepository.findAll(spec));
    }
}
