/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.r2dbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Duration

/**
 * The driver emits one {@link Result} per entity of a batch insert, each with the generated id of that entity. The
 * ids must be read in the order of the results, whenever the rows of each result arrive, and there must be exactly
 * one per entity.
 */
@MicronautTest(transactional = false)
@Property(name = "spec.name", value = "H2BatchGeneratedIdSpec")
class H2BatchGeneratedIdSpec extends Specification implements H2TestPropertyProvider {

    @Inject
    H2BatchGeneratedIdEntityRepository repository

    void cleanup() {
        GeneratedIdResultsTamperer.tampering = GeneratedIdResultsTamperer.Tampering.NONE
    }

    void "assigns each generated id to its own entity when the first result's rows arrive last"() {
        given:
        GeneratedIdResultsTamperer.tampering = GeneratedIdResultsTamperer.Tampering.DELAY_FIRST

        when:
        List<H2BatchGeneratedIdEntity> saved = repository.saveAll(entities("a", "b", "c")).collectList().block()

        then: "each entity has the id of its own row"
        saved*.name == ["a", "b", "c"]
        saved.every { it.id != null }
        saved.collect { repository.findById(it.id).block().name } == ["a", "b", "c"]
    }

    void "fails when the database returns more generated ids than entities"() {
        given:
        GeneratedIdResultsTamperer.tampering = GeneratedIdResultsTamperer.Tampering.DUPLICATE_FIRST

        when:
        repository.saveAll(entities("a", "b", "c")).collectList().block()

        then:
        def e = thrown(DataAccessException)
        e.message.contains("3")
        e.message.contains("4")
    }

    void "fails when the database returns fewer generated ids than entities"() {
        given:
        GeneratedIdResultsTamperer.tampering = GeneratedIdResultsTamperer.Tampering.DROP_FIRST

        when:
        repository.saveAll(entities("a", "b", "c")).collectList().block()

        then:
        thrown(DataAccessException)
    }

    private static List<H2BatchGeneratedIdEntity> entities(String... names) {
        return names.collect { new H2BatchGeneratedIdEntity(name: it) }
    }
}

@MappedEntity("r2_batch_generated_id")
class H2BatchGeneratedIdEntity {
    @Id
    @GeneratedValue
    Long id
    String name
}

@R2dbcRepository(dialect = Dialect.H2)
interface H2BatchGeneratedIdEntityRepository extends ReactorCrudRepository<H2BatchGeneratedIdEntity, Long> {
}

/**
 * Changes the rows of the first {@link Result} of a statement that returns generated values: delays them, emits them
 * twice or drops them.
 */
@Singleton
@Requires(property = "spec.name", value = "H2BatchGeneratedIdSpec")
class GeneratedIdResultsTamperer implements BeanCreatedEventListener<ConnectionFactory> {

    enum Tampering {
        NONE, DELAY_FIRST, DUPLICATE_FIRST, DROP_FIRST
    }

    static volatile Tampering tampering = Tampering.NONE

    @Override
    ConnectionFactory onCreated(BeanCreatedEvent<ConnectionFactory> event) {
        ConnectionFactory connectionFactory = event.bean
        return proxy(ConnectionFactory, connectionFactory) { Method method, Object result ->
            method.name == "create" ? Mono.from(result).map { Connection c -> connection(c) } : result
        }
    }

    private static Connection connection(Connection connection) {
        return proxy(Connection, connection) { Method method, Object result ->
            method.name == "createStatement" ? statement((Statement) result) : result
        }
    }

    private static Statement statement(Statement statement) {
        boolean returnsGeneratedValues = false
        Statement wrapped
        wrapped = proxy(Statement, statement) { Method method, Object result ->
            if (method.name == "returnGeneratedValues") {
                returnsGeneratedValues = true
            }
            if (method.name == "execute" && returnsGeneratedValues && tampering != Tampering.NONE) {
                return Flux.from(result).index().map { indexed ->
                    indexed.t1 == 0 ? tamper((Result) indexed.t2) : indexed.t2
                }
            }
            return result.is(statement) ? wrapped : result
        }
        return wrapped
    }

    private static Result tamper(Result result) {
        return proxy(Result, result) { Method method, Object rows ->
            if (method.name != "map") {
                return rows
            }
            switch (tampering) {
                case Tampering.DELAY_FIRST:
                    return Flux.from(rows).delaySubscription(Duration.ofMillis(200))
                case Tampering.DUPLICATE_FIRST:
                    return Flux.from(rows).flatMap { Flux.just(it, it) }
                case Tampering.DROP_FIRST:
                    return Flux.from(rows).ignoreElements()
                default:
                    return rows
            }
        }
    }

    private static <T> T proxy(Class<T> type, T delegate, Closure<Object> afterCall) {
        return (T) Proxy.newProxyInstance(type.classLoader, [type] as Class[]) { Object p, Method method, Object[] args ->
            Object result
            try {
                result = method.invoke(delegate, args)
            } catch (InvocationTargetException e) {
                throw e.cause
            }
            return afterCall.call(method, result)
        }
    }
}
