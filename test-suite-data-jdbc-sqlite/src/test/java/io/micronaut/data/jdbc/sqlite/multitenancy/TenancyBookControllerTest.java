package io.micronaut.data.jdbc.sqlite.multitenancy;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.url", value = "jdbc:sqlite:file:devDb?mode=memory&cache=shared")
@Property(name = "datasources.default.username", value = "sa")
@Property(name = "datasources.default.password", value = "")
@Property(name = "datasources.default.dialect", value = "ANSI")
@Property(name = "datasources.default.db-type", value = "sqlite")
@Property(name = "datasources.default.driver-class-name", value = "org.sqlite.JDBC")
@Property(name = "micronaut.multitenancy.tenantresolver.httpheader.enabled", value = StringUtils.TRUE)
@Property(name = "datasources.default.packages", value = "io.micronaut.data.jdbc.sqlite.multitenancy")
@Property(name = "spec.name", value = "TenancyBookControllerSpec")
@MicronautTest(transactional = false)
class TenancyBookControllerTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Inject
    TenancyBookRepository bookRepository;

    @AfterEach
    void cleanup() {
        bookRepository.deleteAll();
    }

    @Test
    void multitenancyRequest() {
        BlockingHttpClient client = httpClient.toBlocking();
        save("Building Microservices with Micronaut", "micronaut");
        save("Introducing Micronaut", "micronaut");
        save("Grails 3 - Step by Step", "grails");
        save("Falando de Grail", "grails");
        save("Grails Goodness Notebook", "grails");

        List<TenancyBook> books = fetchBooks(client, "micronaut");
        assertEquals(2, books.size());

        books = fetchBooks(client, "grails");
        assertEquals(3, books.size());
    }

    private List<TenancyBook> fetchBooks(BlockingHttpClient client, String framework) {
        HttpRequest<?> request = HttpRequest.GET("/books").header("tenantId", framework);
        Argument<List<TenancyBook>> responseArgument = Argument.listOf(TenancyBook.class);
        HttpResponse<List<TenancyBook>> response = client.exchange(request, responseArgument);
        assertEquals(HttpStatus.OK, response.getStatus());
        return response.body();
    }

    private void save(String title, String framework) {
        bookRepository.save(new TenancyBook(null, title, framework));
    }
}
