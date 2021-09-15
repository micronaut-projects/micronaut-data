package example

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.transaction.exceptions.NoTransactionException
import jakarta.inject.Inject
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance


@PropertySource(
    Property(name = "flyway.datasources.default.enabled", value = "false"),
    Property(name = "r2dbc.datasources.default.url", value = "r2dbc:h2:mem:///testdb"),
    Property(name = "r2dbc.datasources.default.schema-generate", value = "CREATE_DROP"),
    Property(name = "r2dbc.datasources.default.dialect", value = "H2")
)
@MicronautTest(transactional = false, rollback = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TxTest {

    @Inject
    private lateinit var repository: Parent2Repository

    @Test
    fun `should fail on Flow result type with mandatory TX`() {
        Assertions.assertThrows(NoTransactionException::class.java) {
            runBlocking<Unit> {
                repository.findAll().toList()
            }
        }
    }

    @Test
    fun `should fail on suspend result type with mandatory TX`() = runBlocking<Unit> {
        Assertions.assertThrows(NoTransactionException::class.java) {
            runBlocking<Unit> {
                repository.findById(11111)
            }
        }
    }

}
