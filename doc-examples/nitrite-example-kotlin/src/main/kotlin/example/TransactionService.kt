package example

import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.TransactionOperations
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton

@Singleton
open class TransactionService(
    private val bookRepository: BookRepository,
    private val transactionOperations: TransactionOperations<*>
) {

  // tag::transaction-managed[]
  @Transactional
  open fun saveBook(title: String) {
    bookRepository.save(Book(title))
  }
  // end::transaction-managed[]

  // tag::transaction-manual-rollback[]
  @Transactional
  open fun saveAndRollback(title: String) {
    bookRepository.save(Book(title))
    transactionOperations.executeWrite { status ->
      status.setRollbackOnly()
      null
    }
  }
  // end::transaction-manual-rollback[]

  @Transactional(propagation = TransactionDefinition.Propagation.NOT_SUPPORTED)
  open fun logWithoutTransaction(title: String) {
    bookRepository.save(Book(title))
  }
}
