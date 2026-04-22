package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Property(name = "datasources.default.url", value = "jdbc:sqlite:file:mydb?mode=memory&cache=shared")
@Property(name = "datasources.default.driver-class-name", value = "org.sqlite.JDBC")
// There is no SQLite dialect yet; H2 is used as a substitute.
// See: https://github.com/micronaut-projects/micronaut-data/pull/3820
@Property(name = "datasources.default.dialect", value = "H2")
@Property(name = "datasources.default.schema-generate", value = "NONE")
@MicronautTest(transactional = false)
class BookRepositoryTest {

    @Inject
    DataSource dataSource;

    @BeforeEach
    void setupSchema() throws SQLException {
        try (Connection connection = DelegatingDataSource.unwrapDataSource(dataSource).getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS book (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL
                )
                """);
        }
    }

    @Test
    void defaultConnectionCapabilitiesInstanceDoesNotApplyReadOnlyForSqliteConnections(BookRepository repository) {
        assertDoesNotThrow(repository::findAll);
    }
}
