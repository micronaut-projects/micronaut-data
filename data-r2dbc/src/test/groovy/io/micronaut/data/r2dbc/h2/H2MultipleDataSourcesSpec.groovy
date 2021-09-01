package io.micronaut.data.r2dbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.RxJavaCrudRepository
import io.micronaut.data.tck.entities.Owner
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest(rollback = false)
@Property(name = "r2dbc.datasources.other.url", value = "r2dbc:h2:mem:///otherdb;DB_CLOSE_DELAY=10")
@Property(name = "r2dbc.datasources.other.schema-generate", value = "CREATE_DROP")
@Property(name = "r2dbc.datasources.other.dialect", value = "H2")
class H2MultipleDataSourcesSpec extends Specification implements H2TestPropertyProvider {

    @Inject OtherRepository otherRepository
    @Inject H2OwnerRepository ownerRepository

    void 'test multiple datasources'() {
        when:"An entity is saved in one datasource"
        Mono.from(ownerRepository.deleteAll()).block()
        Flux.from(ownerRepository.saveAll([new Owner("Fred"), new Owner("Bob")])).collectList().block()
        Mono.from(otherRepository.saveAll([new Owner("Joe")])).block()

        then:"Only reflected in one"
        otherRepository.findByName("Joe").isPresent()
        !otherRepository.findByName("Fred").isPresent()
        otherRepository.count().blockingGet() == 1
        Mono.from(ownerRepository.count()).block() == 2
    }

    @R2dbcRepository(value = "other", dialect = Dialect.H2)
    static interface OtherRepository extends RxJavaCrudRepository<Owner, Long> {
        Optional<Owner> findByName(String name)
    }
}
