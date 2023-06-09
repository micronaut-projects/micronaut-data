package io.micronaut.data.r2dbc.h2

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class H2ReactiveAsSyncTransactionalTest extends Specification implements H2TestPropertyProvider {

    @Inject H2BookRepository repository

    void 'test TX operation in test'() {
        when:
            repository.queryById(123456)
        then:
            noExceptionThrown()
    }
}
