package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.*

@MicronautTest(transactional = false, rollback = false)
class TxTest {

    @Inject
    private lateinit var repository: ParentSuspendRepository
    @Inject
    private lateinit var service: PersonSuspendRepositoryService

    @Test
    internal fun `test transaction propagation`() = runBlocking {
        val parent = Parent("xyz", Collections.emptyList())
        val saved = repository.save(parent)

        val found = service.customFind(saved.id!!).get()
        assertTrue(found.name == "xyz")
    }
}
