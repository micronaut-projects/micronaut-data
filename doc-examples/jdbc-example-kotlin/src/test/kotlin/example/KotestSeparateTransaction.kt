package example

import io.kotest.core.spec.style.StringSpec
import io.micronaut.test.annotation.TransactionMode
import io.micronaut.test.extensions.kotest.annotation.MicronautTest

@MicronautTest(transactionMode = TransactionMode.SEPARATE_TRANSACTIONS)
class KotestSeparateTransaction(private var bookRepository: BookRepository): StringSpec({

    "should not fail" {
        bookRepository.deleteAllRequiresTx()
    }
})
