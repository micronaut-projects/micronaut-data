package example;

import com.mongodb.client.MongoClient;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest
class BookDataSourceMultiTenancySpec {

    @Inject
    FooBookClient fooBookClient;

    @Inject
    BarBookClient barBookClient;

    @Inject
    @Named("bar")
    MongoClient barMongoClient;

    @Inject
    @Named("foo")
    MongoClient fooMongoClient;

    @AfterEach
    public void cleanup() {
        fooBookClient.deleteAll();
        barBookClient.deleteAll();
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
        // And: Mongo client validates previous steps
        assertEquals(1, fooMongoClient.getDatabase("test").getCollection("book").countDocuments());
        assertEquals(0, barMongoClient.getDatabase("test").getCollection("book").countDocuments());

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

@Header(name = "tenantId", value = "foo")
@Client("/books")
interface FooBookClient extends BookClient {
}

@Header(name = "tenantId", value = "bar")
@Client("/books")
interface BarBookClient extends BookClient {
}
