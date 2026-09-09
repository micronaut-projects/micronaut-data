package example;

import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration;
import io.micronaut.data.r2dbc.operations.R2dbcSchemaHandler;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
class BookR2dbcSchemaMultiTenancySpec {

    @Inject
    FooBookClient fooBookClient;

    @Inject
    BarBookClient barBookClient;

    @Inject
    ConnectionFactory cf;

    @Inject
    DataR2dbcConfiguration conf;

    @Inject
    R2dbcSchemaHandler schemaHandler;

    @Inject
    @Client("/")
    HttpClient httpClient;

    @AfterEach
    public void cleanup() {
        fooBookClient.deleteAll();
        barBookClient.deleteAll();
    }

    @AfterAll
    public void deleteSchema() {
        deleteSchema("foo");
        deleteSchema("bar");
    }

    @Test
    void testRest() {
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
        assertEquals(1, getSchemaBooksCount("foo"));
        assertEquals(0, getSchemaBooksCount("bar"));

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
    void invalidTenantIdCannotExecuteSql() {
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

    private void deleteSchema(String schemaName) {
        Mono.from(cf.create())
            .flatMap(c -> Flux.from(c.createStatement("DROP SCHEMA " + schemaName + ";").execute())
                .flatMap(Result::getRowsUpdated)
                .then())
            .block();
    }

    protected long getSchemaBooksCount(String schemaName) {
        return Mono.from(cf.create())
            .flatMap(c -> Mono.from(schemaHandler.useSchema(c, conf.getDialect(), schemaName)).thenReturn(c))
            .flatMap(c -> Mono.from(c.createStatement("select count(*) from book").execute()))
            .flatMap(r -> Mono.from(r.map(readable -> (Long) readable.get(0))))
            .block();
    }

    private long getTableCount(String tableName) {
        return Mono.from(cf.create())
            .flatMap(c -> Mono.from(c.createStatement("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '" + tableName + "'").execute()))
            .flatMap(r -> Mono.from(r.map(readable -> (Long) readable.get(0))))
            .block();
    }
}

@Header(name = "tenantId", value = "foo")
@Client("/books")
interface FooBookClient extends BookClient {
}

@Header(name = "tenantId", value = "bar")
@Client("/books")
interface BarBookClient extends BookClient {
}
