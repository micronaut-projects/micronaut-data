package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Property(name = "datasources.default.url", value = "jdbc:sqlite:file:mydb?mode=memory&cache=shared")
@Property(name = "datasources.default.driver-class-name", value = "org.sqlite.JDBC")
@Property(name = "datasources.default.dialect", value = "SQLITE")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@MicronautTest(transactional = false)
class BookRepositoryTest {

    @Test
    void sqliteConnectionCapabilitiesDoesNotApplyReadOnlyForSqliteConnections(BookRepository repository) {
        assertDoesNotThrow(() -> repository.findAll().iterator().hasNext());
    }
}
