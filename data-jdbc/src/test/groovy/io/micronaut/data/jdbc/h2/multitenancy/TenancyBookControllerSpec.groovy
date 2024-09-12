package io.micronaut.data.jdbc.h2.multitenancy

import io.micronaut.context.annotation.Property
import io.micronaut.core.type.Argument
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import static org.junit.Assert.assertEquals

@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
// <1>
@Property(name = "datasources.default.url", value = "jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
@Property(name = "datasources.default.username", value = "sa")
@Property(name = "datasources.default.password", value = "")
@Property(name = "datasources.default.dialect", value = "H2")
@Property(name = "datasources.default.driver-class-name", value = "org.h2.Driver")
@Property(name = "micronaut.multitenancy.tenantresolver.httpheader.enabled", value = StringUtils.TRUE)
@Property(name = "spec.name", value = "TenancyBookControllerSpec")
@MicronautTest(transactional = false)
// <2>
class TenancyBookControllerSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient httpClient

    @Inject
    TenancyBookRepository bookRepository

    def multitenancyRequest() {

        given:
            BlockingHttpClient client = httpClient.toBlocking()
            save(bookRepository, client, "Building Microservices with Micronaut", "micronaut")
            save(bookRepository, client, "Introducing Micronaut", "micronaut")
            save(bookRepository, client, "Grails 3 - Step by Step", "grails")
            save(bookRepository, client, "Falando de Grail", "grails")
            save(bookRepository, client, "Grails Goodness Notebook", "grails")

        when:
            List<TenancyBook> books = fetchBooks(client, "micronaut")
        then:
            books
            books.size() == 2

        when:
            books = fetchBooks(client, "grails")
        then:
            books
            books.size() == 3

        cleanup:
            bookRepository.deleteAll()
    }

    List<TenancyBook> fetchBooks(BlockingHttpClient client, String framework) {
        HttpRequest<?> request = HttpRequest.GET("/books").header("tenantId", framework)
        Argument<List<TenancyBook>> responseArgument = Argument.listOf(TenancyBook.class)
        HttpResponse<List<TenancyBook>> response = client.exchange(request, responseArgument)
        assertEquals(HttpStatus.OK, response.getStatus())
        return response.body()
    }

    void save(TenancyBookRepository bookRepository, BlockingHttpClient client, String title, String framework) {
        bookRepository.save(new TenancyBook(null, title, framework))
    }
}
