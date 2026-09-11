package example;

import io.micronaut.context.annotation.Primary;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

/**
 * Demonstrates transactional usage for Nitrite repositories.
 */
@Singleton
@Primary
public class TransactionService {

  private final BookRepository bookRepository;
  private final TransactionOperations<?> transactionOperations;

  public TransactionService(
      BookRepository bookRepository, TransactionOperations<?> transactionOperations) {
    this.bookRepository = bookRepository;
    this.transactionOperations = transactionOperations;
  }

  // tag::transaction-managed[]
  @Transactional
  public void saveBook(String title) {
    bookRepository.save(new Book(title));
  }
  // end::transaction-managed[]

  // tag::transaction-manual-rollback[]
  @Transactional
  public void saveAndRollback(String title) {
    bookRepository.save(new Book(title));
    transactionOperations.executeWrite(status -> {
      status.setRollbackOnly();
      return null;
    });
  }
  // end::transaction-manual-rollback[]

  @Transactional(propagation = TransactionDefinition.Propagation.NOT_SUPPORTED)
  public void logWithoutTransaction(String title) {
    bookRepository.save(new Book(title));
  }
}
