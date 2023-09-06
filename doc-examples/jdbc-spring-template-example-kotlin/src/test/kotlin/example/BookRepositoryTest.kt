package example

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*

@Property(name = "spec.name", value = "BookRepositoryTest")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.transactionManager", value = "springJdbc")
@Property(name = "jpa.default.properties.hibernate.hbm2ddl.auto", value = "create-drop")
@MicronautTest
internal class BookRepositoryTest {

    @Inject
    var abstractBookRepository: AbstractBookRepository? = null

    @AfterEach
    fun cleanup() {
        abstractBookRepository!!.deleteAll()
    }

    @Test
    fun testBooksJdbcTemplate() {
        abstractBookRepository!!.saveAll(
            Arrays.asList(
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

        val result = abstractBookRepository!!.findByTitle("The Shining")
        Assertions.assertEquals(1, result.size)
    }
}
