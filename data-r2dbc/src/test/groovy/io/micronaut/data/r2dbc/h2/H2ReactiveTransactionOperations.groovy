package io.micronaut.data.r2dbc.h2


import io.micronaut.data.tck.repositories.SimpleReactiveBookRepository
import io.micronaut.data.tck.tests.AbstractReactiveTransactionSpec

class H2ReactiveTransactionOperations extends AbstractReactiveTransactionSpec implements H2TestPropertyProvider {

    @Override
    Class<? extends SimpleReactiveBookRepository> getBookRepositoryClass() {
        return H2BookReactiveRepository.class
    }
}
