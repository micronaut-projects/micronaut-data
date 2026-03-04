package example

import io.micronaut.data.nitrite.repository.BookRepository
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.TransactionOperations
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton

@Singleton
class TransactionService {

  private final BookRepository bookRepository
  private final TransactionOperations<?> transactionOperations

  TransactionService(
      BookRepository bookRepository, TransactionOperations<?> transactionOperations) {
    this.bookRepository = bookRepository
    this.transactionOperations = transactionOperations
  }

  // tag::transaction-managed[]
  @Transactional
  void saveBook(String title) {
    bookRepository.save(new Book(title))
  }
  // end::transaction-managed[]

  // tag::transaction-manual-rollback[]
  @Transactional
  void saveAndRollback(String title) {
    bookRepository.save(new Book(title))
    transactionOperations.executeWrite { status ->
      status.setRollbackOnly()
      null
    }
  }
  // end::transaction-manual-rollback[]

  @Transactional(propagation = TransactionDefinition.Propagation.NOT_SUPPORTED)
  void logWithoutTransaction(String title) {
    bookRepository.save(new Book(title))
  }
}
