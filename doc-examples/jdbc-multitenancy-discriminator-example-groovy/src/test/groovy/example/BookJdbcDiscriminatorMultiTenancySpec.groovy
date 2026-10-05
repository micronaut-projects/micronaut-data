package example

import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class BookJdbcDiscriminatorMultiTenancySpec extends Specification {

    @Inject
    FooBookClient fooBookClient

    @Inject
    BarBookClient barBookClient

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

        and: "No tenancy repository methods returns all books"
        barBookClient.findAllWithoutTenancy().size() == 1
        fooBookClient.findAllWithoutTenancy().size() == 1

        when: "Delete all BARs"
        barBookClient.deleteAll()

        then: "FOOs aren't deletes"
        fooBookClient.findAll().size() == 1

        when: "Delete all FOOs"
        fooBookClient.deleteAll()

        then: "BARs aren deletes"
        fooBookClient.findAll().size() == 0
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
