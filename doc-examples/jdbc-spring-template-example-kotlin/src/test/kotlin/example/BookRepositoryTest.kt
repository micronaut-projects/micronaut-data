package example

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@MicronautTest
@Property(name = "spec.name", value = "BookRepositoryTest")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.transactionManager", value = "springJdbc")
@Property(name = "jpa.default.properties.hibernate.hbm2ddl.auto", value = "create-drop")
internal class BookRepositoryTest {

    @Inject
    lateinit var bookRepository: AbstractBookRepository

    @AfterEach
    fun cleanup() {
        bookRepository.deleteAll()
    }

    @Test
    fun testBooksJdbcTemplate() {
        bookRepository.saveAll(
            listOf(
                Book(null, "The Stand", 1000),
                Book(null, "The Shining", 600),
                Book(null, "The Power of the Dog", 500),
                Book(null, "The Border", 700),
                Book(null, "Along Came a Spider", 300),
                Book(null, "Pet Cemetery", 400),
                Book(null, "A Game of Thrones", 900),
                Book(null, "A Clash of Kings", 1100)
            )
        )

        val result = bookRepository.findByTitle("The Shining")
        assertEquals(1, result.size)

        assertNotNull(result[0].id)
        assertEquals("The Shining", result[0].title)
        assertEquals(600, result[0].pages)
    }
}
