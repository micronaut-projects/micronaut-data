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
package io.micronaut.data.r2dbc.h2.schemagen

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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

class H2SchemaGeneratorSpec extends Specification {

    void "schema generation with batch-generate=#batchGenerate and #schemaGenerate"() {
        given:
            def context = ApplicationContext.run([
                    'spec.name'                                : 'H2SchemaGeneratorSpec',
                    'r2dbc.datasources.default.url'            : "r2dbc:h2:mem:///schemagen${batchGenerate}${schemaGenerate};DB_CLOSE_DELAY=10",
                    'r2dbc.datasources.default.username'       : '',
                    'r2dbc.datasources.default.password'       : '',
                    'r2dbc.datasources.default.dialect'        : 'h2',
                    'r2dbc.datasources.default.schema-generate': schemaGenerate.name(),
                    'r2dbc.datasources.default.batch-generate' : batchGenerate,
                    'r2dbc.datasources.default.packages'       : getClass().package.name
            ])
            def createStatements = context.getBean(StatementRecorder).statements.findAll { it.contains("CREATE TABLE") }

        when:
            def authorRepository = context.getBean(SchemaGenAuthorRepository)
            def bookRepository = context.getBean(SchemaGenBookRepository)
            authorRepository.save(new SchemaGenAuthor(name: "Stephen King")).block()
            bookRepository.save(new SchemaGenBook(title: "The Stand")).block()

        then: "the tables are created"
            authorRepository.count().block() == 1
            bookRepository.count().block() == 1

        and: "the tables are created with a single statement only when batch generation is enabled"
            if (batchGenerate) {
                assert createStatements.size() == 1
                assert createStatements[0].contains("schema_gen_author")
                assert createStatements[0].contains("schema_gen_book")
            } else {
                assert createStatements.size() == 2
                assert createStatements.every { !(it.contains("schema_gen_author") && it.contains("schema_gen_book")) }
            }

        cleanup:
            context?.close()

        where:
            batchGenerate | schemaGenerate
            false         | SchemaGenerate.CREATE
            false         | SchemaGenerate.CREATE_DROP
            true          | SchemaGenerate.CREATE
            true          | SchemaGenerate.CREATE_DROP
    }

    void "batch CREATE_DROP drops and recreates existing tables"() {
        given: "a database whose tables already exist"
            def config = [
                    'spec.name'                                : 'H2SchemaGeneratorSpec',
                    'r2dbc.datasources.default.url'            : "r2dbc:h2:mem:///schemagenexisting;DB_CLOSE_DELAY=-1",
                    'r2dbc.datasources.default.username'       : '',
                    'r2dbc.datasources.default.password'       : '',
                    'r2dbc.datasources.default.dialect'        : 'h2',
                    'r2dbc.datasources.default.batch-generate' : true,
                    'r2dbc.datasources.default.packages'       : getClass().package.name
            ]
            def firstContext = ApplicationContext.run(config + ['r2dbc.datasources.default.schema-generate': SchemaGenerate.CREATE.name()])
            firstContext.getBean(SchemaGenAuthorRepository).save(new SchemaGenAuthor(name: "Stephen King")).block()
            firstContext.close()

        when: "the schema is generated again with a batch drop"
            def context = ApplicationContext.run(config + ['r2dbc.datasources.default.schema-generate': SchemaGenerate.CREATE_DROP.name()])
            def dropStatements = context.getBean(StatementRecorder).statements.findAll { it.contains("DROP TABLE") }

        then: "every table is dropped and created again"
            dropStatements.size() == 2
            dropStatements.any { it.contains("schema_gen_author") }
            dropStatements.any { it.contains("schema_gen_book") }
            context.getBean(SchemaGenAuthorRepository).count().block() == 0

        cleanup:
            context?.close()
    }

    void "batch CREATE_DROP drops the existing tables when another table doesn't exist"() {
        given: "a database with the data of both entities"
            def config = [
                    'spec.name'                                : 'H2SchemaGeneratorSpec',
                    'r2dbc.datasources.default.url'            : "r2dbc:h2:mem:///schemagenmissing;DB_CLOSE_DELAY=-1",
                    'r2dbc.datasources.default.username'       : '',
                    'r2dbc.datasources.default.password'       : '',
                    'r2dbc.datasources.default.dialect'        : 'h2',
                    'r2dbc.datasources.default.batch-generate' : true,
                    'r2dbc.datasources.default.packages'       : getClass().package.name
            ]
            def firstContext = ApplicationContext.run(config + ['r2dbc.datasources.default.schema-generate': SchemaGenerate.CREATE.name()])
            firstContext.getBean(SchemaGenAuthorRepository).save(new SchemaGenAuthor(name: "Stephen King")).block()
            firstContext.getBean(SchemaGenBookRepository).save(new SchemaGenBook(title: "The Stand")).block()

        and: "the table that is dropped first doesn't exist, as for an entity added since the last run"
            def createStatement = firstContext.getBean(StatementRecorder).statements.find { it.contains("CREATE TABLE") }
            def firstTable = (createStatement =~ /CREATE TABLE (\S*schema_gen_(?:author|book)\S*)/)[0][1]
            Mono.usingWhen(Mono.from(firstContext.getBean(ConnectionFactory).create()),
                    { Connection connection -> Flux.from(connection.createStatement("DROP TABLE " + firstTable).execute()).flatMap { it.getRowsUpdated() }.then() },
                    { Connection connection -> connection.close() }).block()
            firstContext.close()

        when: "the schema is generated again"
            def context = ApplicationContext.run(config + ['r2dbc.datasources.default.schema-generate': SchemaGenerate.CREATE_DROP.name()])

        then: "the existing table is dropped too, so no previous data is kept"
            context.getBean(SchemaGenAuthorRepository).count().block() == 0
            context.getBean(SchemaGenBookRepository).count().block() == 0

        cleanup:
            context?.close()
    }
}

/**
 * Records the SQL of every statement created by the R2DBC connections.
 */
@Requires(property = "spec.name", value = "H2SchemaGeneratorSpec")
@Singleton
class StatementRecorder implements BeanCreatedEventListener<ConnectionFactory> {

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
        return (T) Proxy.newProxyInstance(StatementRecorder.classLoader, [type] as Class[], { Object proxy, Method method, Object[] args ->
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

@MappedEntity
class SchemaGenAuthor {
    @Id
    @GeneratedValue
    Long id
    String name
}

@MappedEntity
class SchemaGenBook {
    @Id
    @GeneratedValue
    Long id
    String title
}

@R2dbcRepository(dialect = Dialect.H2)
interface SchemaGenAuthorRepository extends ReactorCrudRepository<SchemaGenAuthor, Long> {
}

@R2dbcRepository(dialect = Dialect.H2)
interface SchemaGenBookRepository extends ReactorCrudRepository<SchemaGenBook, Long> {
}
