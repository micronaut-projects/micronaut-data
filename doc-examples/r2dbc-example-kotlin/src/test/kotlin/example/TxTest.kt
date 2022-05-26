package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.transaction.exceptions.NoTransactionException
import jakarta.inject.Inject
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance


@MicronautTest(transactional = false, rollback = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TxTest : AbstractTest(false) {

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
