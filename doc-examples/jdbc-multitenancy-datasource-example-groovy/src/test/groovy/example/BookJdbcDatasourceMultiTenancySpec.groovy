package example

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

@MicronautTest(transactional = false)
class BookJdbcDatasourceMultiTenancySpec extends Specification {

    @Inject
    FooBookClient fooBookClient

    @Inject
    BarBookClient barBookClient

    @Inject
    @Named("bar")
    DataSource barDataSource

    @Inject
    @Named("foo")
    DataSource fooDataSource

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
        getBooksCount(fooDataSource) == 1
        getBooksCount(barDataSource) == 0

        when: "Delete all BARs"
        barBookClient.deleteAll()

        then: "FOOs aren't deletes"
        fooBookClient.findAll().size() == 1

        when: "Delete all FOOs"
        fooBookClient.deleteAll()

        then: "BARs aren deletes"
        fooBookClient.findAll().size() == 0
    }

    private static long getBooksCount(DataSource ds) {
        if (ds instanceof DelegatingDataSource) {
            ds = ((DelegatingDataSource) ds).targetDataSource
        }
        Connection connection = ds.connection
        try {
            PreparedStatement ps = connection.prepareStatement("select count(*) from book")
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
