package example

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
class BookJdbcDatasourceMultiTenancySpec {

    @Inject
    lateinit var fooBookClient: FooBookClient

    @Inject
    lateinit var barBookClient: BarBookClient

    @Inject
    @field:Named("bar")
    lateinit var barDataSource: DataSource

    @Inject
    @field:Named("foo")
    lateinit var fooDataSource: DataSource

    @AfterEach
    fun cleanup() {
        fooBookClient.deleteAll()
        barBookClient.deleteAll()
    }

    @Test
    fun testRest() {
        // When: A book created in FOO tenant
        var book: BookDto? = fooBookClient.save("The Stand", 1000)
        assertNotNull(book!!.id)
        // Then: The book exists in FOO tenant
        book = fooBookClient.findOne(book.id).orElse(null)
        assertNotNull(book)
        assertEquals("The Stand", book!!.title)
        // And: There is one book
        assertEquals(1, fooBookClient.findAll().size)
        assertTrue(fooBookClient.findAll().iterator().hasNext())
        // And: There is no books in BAR tenant
        assertEquals(0, barBookClient.findAll().size)
        // And: JDBC client validates previous steps
        assertEquals(1, getBooksCount(fooDataSource))
        assertEquals(0, getBooksCount(barDataSource))

        // When: Delete all BARs
        barBookClient.deleteAll()
        // Then: FOOs aren't deletes
        assertEquals(1, fooBookClient.findAll().size)

        // When: Delete all FOOs
        fooBookClient.deleteAll()
        // Then: BARs aren deletes
        assertEquals(0, fooBookClient.findAll().size)
    }

    private fun getBooksCount(dataSource: DataSource): Long {
        val ds = if (dataSource is DelegatingDataSource) dataSource.targetDataSource else dataSource
        ds.connection.use { connection ->
            connection.prepareStatement("select count(*) from book").use { ps ->
                ps.executeQuery().use { resultSet ->
                    resultSet.next()
                    return resultSet.getLong(1)
                }
            }
        }
    }
}

// tag::clients[]

@Header(name = "tenantId", value = "foo")
@Client("/books")
interface FooBookClient : BookClient

@Header(name = "tenantId", value = "bar")
@Client("/books")
interface BarBookClient : BookClient

// end::clients[]
