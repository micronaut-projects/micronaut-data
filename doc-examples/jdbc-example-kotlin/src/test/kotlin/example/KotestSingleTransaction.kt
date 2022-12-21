package example

import io.kotest.core.spec.style.StringSpec
import io.micronaut.test.annotation.TransactionMode
import io.micronaut.test.extensions.kotest.annotation.MicronautTest
import org.junit.jupiter.api.Test

@MicronautTest(transactionMode = TransactionMode.SINGLE_TRANSACTION)
class KotestSingleTransaction(private var bookRepository: BookRepository): StringSpec({

    @Test
    fun shouldNotFail() {
        bookRepository.deleteAllRequiresTx()
    }
})
