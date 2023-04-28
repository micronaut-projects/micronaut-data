package io.micronaut.data.document.mongodb

import io.micronaut.data.document.mongodb.repositories.MongoBookRepository
import io.micronaut.data.document.tck.AbstractDocumentTransactionSpec
import io.micronaut.data.document.tck.repositories.BookRepository
import io.micronaut.data.mongodb.session.MongoConnectionOperations
import io.micronaut.data.mongodb.session.MongoConnectionOperationsImpl
import io.micronaut.data.mongodb.transaction.MongoTransactionOperations
import io.micronaut.data.mongodb.transaction.MongoTransactionOperationsImpl
import io.micronaut.transaction.TransactionOperations

class MongoDocumentTransactionSpec extends AbstractDocumentTransactionSpec implements MongoTestPropertyProvider {

    @Override
    Class<? extends BookRepository> getBookRepositoryClass() {
        return MongoBookRepository.class
    }

    @Override
    protected TransactionOperations getTransactionOperations() {
        return context.getBean(MongoTransactionOperations)
    }

    @Override
    protected Runnable getNoTxCheck() {
        MongoConnectionOperations connectionOperations = context.getBean(MongoConnectionOperations)
        return new Runnable() {
            @Override
            void run() {
                assert !connectionOperations.connectionStatus.connection.hasActiveTransaction()
            }
        }
    }

    @Override
    boolean supportsReadOnlyFlag() {
        return false
    }
}
