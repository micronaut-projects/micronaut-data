package example;

import io.micronaut.core.type.Argument;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.operations.JdbcSchemaHandler;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
class BookJdbcSchemaMultiTenancySpec {

    @Inject
    FooBookClient fooBookClient;

    @Inject
    BarBookClient barBookClient;

    @Inject
    DataSource dataSource;

    @Inject
    JdbcSchemaHandler jdbcSchemaHandler;

    @Inject
    @Client("/")
    HttpClient httpClient;

    @AfterEach
    public void cleanup() {
        fooBookClient.deleteAll();
        barBookClient.deleteAll();
    }

    @Test
    void testRest() throws SQLException {
        // When: A book created in FOO tenant
        BookDto book = fooBookClient.save("The Stand", 1000);
        assertNotNull(book.getId());
        // Then: The book exists in FOO tenant
        book = fooBookClient.findOne(book.getId()).orElse(null);
        assertNotNull(book);
        assertEquals("The Stand", book.getTitle());
        // And: There is one book
        assertEquals(1, fooBookClient.findAll().size());
        assertTrue(fooBookClient.findAll().iterator().hasNext());
        // And: There is no books in BAR tenant
        assertEquals(0, barBookClient.findAll().size());
        // And: JDBC client validates previous steps
        assertEquals(1, getBooksCount("foo"));
        assertEquals(0, getBooksCount("bar"));

        // When: Delete all BARs
        barBookClient.deleteAll();
        // Then: FOOs aren't deletes
        assertEquals(1, fooBookClient.findAll().size());

        // When: Delete all FOOs
        fooBookClient.deleteAll();
        // Then: BARs aren deletes
        assertEquals(0, fooBookClient.findAll().size());
    }

    @Test
    void invalidTenantIdCannotExecuteSql() throws SQLException {
        String payload = "PUBLIC; CREATE TABLE PWNED(id int); --";
        boolean failed = false;

        try {
            httpClient.toBlocking().exchange(
                HttpRequest.GET("/books").header("tenantId", payload),
                Argument.listOf(BookDto.class)
            );
        } catch (HttpClientResponseException | DataAccessException ignored) {
            // The quoted schema does not exist, so the repository operation is expected to fail.
            failed = true;
        }

        assertTrue(failed);
        assertEquals(0, getTableCount("PWNED"));
    }

    private long getBooksCount(String schemaName) throws SQLException {
        if (dataSource instanceof DelegatingDataSource delegatingDataSource) {
            dataSource = delegatingDataSource.getTargetDataSource();
        }
        try (Connection connection = dataSource.getConnection()) {
            jdbcSchemaHandler.useSchema(connection, Dialect.H2, schemaName);
            try (PreparedStatement ps = connection.prepareStatement("select count(*) from book")) {
                try (ResultSet resultSet = ps.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        }
    }

    private long getTableCount(String tableName) throws SQLException {
        DataSource targetDataSource = dataSource;
        if (targetDataSource instanceof DelegatingDataSource delegatingDataSource) {
            // Bypass the tenant connection advice while inspecting the default schema.
            targetDataSource = delegatingDataSource.getTargetDataSource();
        }
        try (Connection connection = targetDataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?")) {
            ps.setString(1, tableName);
            try (ResultSet resultSet = ps.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}

// tag::clients[]

@Header(name = "tenantId", value = "foo")
@Client("/books")
interface FooBookClient extends BookClient {
}

@Header(name = "tenantId", value = "bar")
@Client("/books")
interface BarBookClient extends BookClient {
}

// end::clients[]
