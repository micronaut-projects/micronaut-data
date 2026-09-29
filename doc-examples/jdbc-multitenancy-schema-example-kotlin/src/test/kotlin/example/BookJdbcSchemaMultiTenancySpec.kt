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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
class BookJdbcSchemaMultiTenancySpec {

    @Inject
    lateinit var fooBookClient: FooBookClient

    @Inject
    lateinit var barBookClient: BarBookClient

    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var jdbcSchemaHandler: JdbcSchemaHandler

    @Inject
    @field:Client("/")
    lateinit var httpClient: HttpClient

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
        assertEquals(1, getBooksCount("foo"))
        assertEquals(0, getBooksCount("bar"))

        // When: Delete all BARs
        barBookClient.deleteAll()
        // Then: FOOs aren't deletes
        assertEquals(1, fooBookClient.findAll().size)

        // When: Delete all FOOs
        fooBookClient.deleteAll()
        // Then: BARs aren deletes
        assertEquals(0, fooBookClient.findAll().size)
    }

    @Test
    fun invalidTenantIdCannotExecuteSql() {
        val payload = "PUBLIC; CREATE TABLE PWNED(id int); --"
        var failed = false

        try {
            httpClient.toBlocking().exchange(
                HttpRequest.GET<Any>("/books").header("tenantId", payload),
                Argument.listOf(BookDto::class.java)
            )
        } catch (ignored: HttpClientResponseException) {
            // The quoted schema does not exist, so the repository operation is expected to fail.
            failed = true
        } catch (ignored: DataAccessException) {
            failed = true
        }

        assertTrue(failed)
        assertEquals(0, getTableCount("PWNED"))
    }

    private fun targetDataSource(): DataSource {
        val ds = dataSource
        // Bypass the tenant connection advice while inspecting the database directly.
        return if (ds is DelegatingDataSource) ds.targetDataSource else ds
    }

    private fun getBooksCount(schemaName: String): Long {
        targetDataSource().connection.use { connection ->
            jdbcSchemaHandler.useSchema(connection, Dialect.H2, schemaName)
            connection.prepareStatement("select count(*) from book").use { ps ->
                ps.executeQuery().use { resultSet ->
                    resultSet.next()
                    return resultSet.getLong(1)
                }
            }
        }
    }

    private fun getTableCount(tableName: String): Long {
        targetDataSource().connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?").use { ps ->
                ps.setString(1, tableName)
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
