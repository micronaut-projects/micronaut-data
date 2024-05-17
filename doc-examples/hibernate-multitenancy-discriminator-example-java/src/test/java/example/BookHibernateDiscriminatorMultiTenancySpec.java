package example;

import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
class BookHibernateDiscriminatorMultiTenancySpec {

    @Inject
    FooBookClient fooBookClient;

    @Inject
    BarBookClient barBookClient;

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
        // No tenancy repository methods returns all books
        assertEquals(1, barBookClient.findAllWithoutTenancy().size());
        assertEquals(1, fooBookClient.findAllWithoutTenancy().size());

        // When: Delete all BARs
        barBookClient.deleteAll();
        // Then: FOOs aren't deletes
        assertEquals(1, fooBookClient.findAll().size());

        // When: Delete all FOOs
        fooBookClient.deleteAll();
        // Then: BARs aren deletes
        assertEquals(0, fooBookClient.findAll().size());
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

