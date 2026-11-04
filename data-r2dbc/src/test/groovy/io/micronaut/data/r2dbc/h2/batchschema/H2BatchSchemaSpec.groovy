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
package io.micronaut.data.r2dbc.h2.batchschema

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.data.runtime.config.SchemaGenerate
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

class H2BatchSchemaSpec extends Specification {

    void "batch generation creates the tables of several entities in the same schema"() {
        given:
            def context = ApplicationContext.run([
                    'spec.name'                                : 'H2BatchSchemaSpec',
                    'r2dbc.datasources.default.url'            : "r2dbc:h2:mem:///schemagensharedschema;DB_CLOSE_DELAY=10",
                    'r2dbc.datasources.default.username'       : '',
                    'r2dbc.datasources.default.password'       : '',
                    'r2dbc.datasources.default.dialect'        : 'h2',
                    'r2dbc.datasources.default.schema-generate': SchemaGenerate.CREATE.name(),
                    'r2dbc.datasources.default.batch-generate' : true,
                    'r2dbc.datasources.default.packages'       : getClass().package.name
            ])
            def createStatements = context.getBean(BatchStatementRecorder).statements.findAll { it.contains("CREATE") }
            def shelfRepository = context.getBean(SchemaShelfRepository)
            def boxRepository = context.getBean(SchemaBoxRepository)

        when:
            shelfRepository.save(new SchemaShelf(name: "Top")).block()
            boxRepository.save(new SchemaBox(name: "Red")).block()

        then: "the shared schema is created once, by the batch"
            createStatements.size() == 1
            createStatements[0].count("CREATE SCHEMA") == 1
            shelfRepository.count().block() == 1
            boxRepository.count().block() == 1

        cleanup:
            context?.close()
    }

    void "batch generation keeps working when the schema and the tables already exist"() {
        given: "a database whose schema and tables already exist"
            def config = [
                    'r2dbc.datasources.default.url'            : "r2dbc:h2:mem:///schemagenexistingschema;DB_CLOSE_DELAY=-1",
                    'r2dbc.datasources.default.username'       : '',
                    'r2dbc.datasources.default.password'       : '',
                    'r2dbc.datasources.default.dialect'        : 'h2',
                    'r2dbc.datasources.default.schema-generate': SchemaGenerate.CREATE.name(),
                    'r2dbc.datasources.default.batch-generate' : true,
                    'r2dbc.datasources.default.packages'       : getClass().package.name
            ]
            def firstContext = ApplicationContext.run(config)
            firstContext.getBean(SchemaShelfRepository).save(new SchemaShelf(name: "Top")).block()
            firstContext.close()

        when: "the application starts again"
            def context = ApplicationContext.run(config)

        then: "the existing tables are kept"
            context.getBean(SchemaShelfRepository).count().block() == 1
            context.getBean(SchemaBoxRepository).count().block() == 0

        cleanup:
            context?.close()
    }
}

@MappedEntity(value = "schema_shelf", schema = "schemagen")
class SchemaShelf {
    @Id
    @GeneratedValue
    Long id
    String name
}

@MappedEntity(value = "schema_box", schema = "schemagen")
class SchemaBox {
    @Id
    @GeneratedValue
    Long id
    String name
}

@R2dbcRepository(dialect = Dialect.H2)
interface SchemaShelfRepository extends ReactorCrudRepository<SchemaShelf, Long> {
}

@R2dbcRepository(dialect = Dialect.H2)
interface SchemaBoxRepository extends ReactorCrudRepository<SchemaBox, Long> {
}

/**
 * Records the SQL of every statement created by the R2DBC connections.
 */
@Requires(property = "spec.name", value = "H2BatchSchemaSpec")
@Singleton
class BatchStatementRecorder implements BeanCreatedEventListener<ConnectionFactory> {

    final List<String> statements = new CopyOnWriteArrayList<>()

    @Override
    ConnectionFactory onCreated(BeanCreatedEvent<ConnectionFactory> event) {
        return proxy(ConnectionFactory, event.bean) { Method method, Object[] args, Object result ->
            if (method.name == "create") {
                return Mono.from(result).map { Connection connection -> recordStatements(connection) }
            }
            return result
        }
    }

    private Connection recordStatements(Connection connection) {
        return proxy(Connection, connection) { Method method, Object[] args, Object result ->
            if (method.name == "createStatement") {
                statements.add(args[0] as String)
            }
            return result
        }
    }

    private static <T> T proxy(Class<T> type, T delegate, Closure<Object> handler) {
        return (T) Proxy.newProxyInstance(BatchStatementRecorder.classLoader, [type] as Class[], { Object proxy, Method method, Object[] args ->
            Object result
            try {
                result = method.invoke(delegate, args)
            } catch (InvocationTargetException e) {
                throw e.cause
            }
            return handler.call(method, args, result)
        } as InvocationHandler)
    }
}
