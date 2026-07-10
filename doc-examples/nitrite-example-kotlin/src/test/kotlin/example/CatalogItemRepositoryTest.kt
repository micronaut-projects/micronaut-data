package example

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.exceptions.UniqueConstraintException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class CatalogItemRepositoryTest {

    @Inject
    lateinit var repository: CatalogItemRepository

    @AfterEach
    fun cleanup() {
        repository.deleteAll()
    }

    // tag::unique-index-usage[]
    @Test
    fun testUniqueIndexRejectsDuplicate() {
        repository.save(CatalogItem("SKU-100", "Widget"))

        val duplicate = CatalogItem("SKU-100", "Different Widget")

        val e = assertThrows(DataAccessException::class.java) { repository.save(duplicate) }
        assertInstanceOf(UniqueConstraintException::class.java, e.cause)
    }
    // end::unique-index-usage[]
}
