package io.micronaut.data.r2dbc.h2

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.StudentReactiveRepository
import io.micronaut.data.tck.tests.AbstractReactiveRepositorySpec
import io.micronaut.transaction.reactive.ReactiveTransactionStatus
import io.r2dbc.spi.Connection
import reactor.core.publisher.Mono

class H2ReactiveRepositorySpec extends AbstractReactiveRepositorySpec implements H2TestPropertyProvider {

    @Memoized
    @Override
    H2ReactivePersonRepository getPersonRepository() {
        return context.getBean(H2ReactivePersonRepository)

    }

    @Memoized
    @Override
    StudentReactiveRepository getStudentRepository() {
        return context.getBean(H2StudentReactiveRepository)
    }

    void 'test with transactional connection'() {
        given:
        R2dbcOperations r2dbcOperations = context.getBean(R2dbcOperations)

        personRepository.save(new Person(name: "Tony")).block()

        when:
        Person person = Mono.from(r2dbcOperations.withTransaction({ ReactiveTransactionStatus<Connection> status ->
            personRepository.findByName("Tony", status).toFlowable()
        })).block()

        then:
        person != null
    }
}
