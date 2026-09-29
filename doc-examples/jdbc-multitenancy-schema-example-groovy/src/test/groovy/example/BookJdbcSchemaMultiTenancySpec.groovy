package example

import io.micronaut.core.type.Argument
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.operations.JdbcSchemaHandler
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

@MicronautTest(transactional = false)
class BookJdbcSchemaMultiTenancySpec extends Specification {

    @Inject
    FooBookClient fooBookClient

    @Inject
    BarBookClient barBookClient

    @Inject
    DataSource dataSource

    @Inject
    JdbcSchemaHandler jdbcSchemaHandler

    @Inject
    @Client("/")
    HttpClient httpClient

    void cleanup() {
        fooBookClient.deleteAll()
        barBookClient.deleteAll()
    }

    void "test rest"() {
        when: "A book created in FOO tenant"
        BookDto book = fooBookClient.save("The Stand", 1000)

        then:
        book.id

        when: "The book exists in FOO tenant"
        book = fooBookClient.findOne(book.id).orElse(null)

        then:
        book
        book.title == "The Stand"

        and: "There is one book"
        fooBookClient.findAll().size() == 1
        fooBookClient.findAll().iterator().hasNext()

        and: "There is no books in BAR tenant"
        barBookClient.findAll().size() == 0

        and: "JDBC client validates previous steps"
        getBooksCount("foo") == 1
        getBooksCount("bar") == 0

        when: "Delete all BARs"
        barBookClient.deleteAll()

        then: "FOOs aren't deletes"
        fooBookClient.findAll().size() == 1

        when: "Delete all FOOs"
        fooBookClient.deleteAll()

        then: "BARs aren deletes"
        fooBookClient.findAll().size() == 0
    }

    void "invalid tenant id cannot execute SQL"() {
        given:
        String payload = "PUBLIC; CREATE TABLE PWNED(id int); --"
        boolean failed = false

        when:
        try {
            httpClient.toBlocking().exchange(
                    HttpRequest.GET("/books").header("tenantId", payload),
                    Argument.listOf(BookDto)
            )
        } catch (HttpClientResponseException | DataAccessException ignored) {
            // The quoted schema does not exist, so the repository operation is expected to fail.
            failed = true
        }

        then:
        failed
        getTableCount("PWNED") == 0
    }

    private DataSource targetDataSource() {
        // Bypass the tenant connection advice while inspecting the database directly.
        return dataSource instanceof DelegatingDataSource ? ((DelegatingDataSource) dataSource).targetDataSource : dataSource
    }

    private long getBooksCount(String schemaName) {
        Connection connection = targetDataSource().connection
        try {
            jdbcSchemaHandler.useSchema(connection, Dialect.H2, schemaName)
            PreparedStatement ps = connection.prepareStatement("select count(*) from book")
            ResultSet resultSet = ps.executeQuery()
            resultSet.next()
            return resultSet.getLong(1)
        } finally {
            connection.close()
        }
    }

    private long getTableCount(String tableName) {
        Connection connection = targetDataSource().connection
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?")
            ps.setString(1, tableName)
            ResultSet resultSet = ps.executeQuery()
            resultSet.next()
            return resultSet.getLong(1)
        } finally {
            connection.close()
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
