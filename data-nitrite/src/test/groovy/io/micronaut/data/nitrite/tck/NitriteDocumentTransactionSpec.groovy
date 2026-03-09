package io.micronaut.data.nitrite.tck

import io.micronaut.data.document.tck.AbstractDocumentTransactionSpec
import io.micronaut.data.document.tck.repositories.BookRepository
import io.micronaut.data.nitrite.tck.NitriteBookRepository
import io.micronaut.data.nitrite.transaction.NitriteTransactionHolder
import io.micronaut.data.nitrite.transaction.NitriteTransactionManager
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.transaction.TransactionOperations

class NitriteDocumentTransactionSpec extends AbstractDocumentTransactionSpec implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return [
            'micronaut.data.nitrite.in-memory': 'true'
        ]
    }

    @Override
    Class<? extends BookRepository> getBookRepositoryClass() {
        return NitriteBookRepository.class
    }

    @Override
    protected TransactionOperations getTransactionOperations() {
        return context.getBean(NitriteTransactionManager)
    }

    @Override
    protected Runnable getNoTxCheck() {
        NitriteTransactionHolder holder = context.getBean(NitriteTransactionHolder)
        return new Runnable() {
            @Override
            void run() {
                assert !holder.isActive()
            }
        }
    }

    @Override
    boolean supportsReadOnlyFlag() {
        return false
    }

    @Override
    boolean supportsModificationInNonTransaction() {
        return true
    }
}
