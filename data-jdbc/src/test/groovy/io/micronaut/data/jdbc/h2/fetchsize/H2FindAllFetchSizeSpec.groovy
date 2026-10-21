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
package io.micronaut.data.jdbc.h2.fetchsize

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.data.annotation.Fetch
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Singleton
import spock.lang.Specification

import javax.sql.DataSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Stream

class H2FindAllFetchSizeSpec extends Specification {

    void "the fetch size of the driver is used for queries reading all their results when it is not configured"() {
        given:
            def context = startContext([:])
            def repository = context.getBean(FetchSizeNoteRepository)
            def recorder = context.getBean(FetchSizeRecorder)
            repository.saveAll((1..5).collect { new FetchSizeNote(text: "note $it") })
            recorder.fetchSizes.clear()

        when:
            def notes = repository.findAll()
            def page = repository.findAll(Pageable.from(0, 2))

        then:
            notes.size() == 5
            page.content.size() == 2
            recorder.fetchSizes.isEmpty()

        cleanup:
            context?.close()
    }

    void "the configured fetch size is used for queries reading all their results"() {
        given:
            def context = startContext(['datasources.default.find-all-fetch-size': 500])
            def repository = context.getBean(FetchSizeNoteRepository)
            def recorder = context.getBean(FetchSizeRecorder)
            repository.saveAll((1..5).collect { new FetchSizeNote(text: "note $it") })
            recorder.fetchSizes.clear()

        when:
            def notes = repository.findAll()

        then:
            notes.size() == 5
            recorder.fetchSizes == [500]

        when: "a page reads its content and its count"
            recorder.fetchSizes.clear()
            def page = repository.findAll(Pageable.from(0, 2))

        then:
            page.content.size() == 2
            page.totalSize == 5
            recorder.fetchSizes == [500]

        when: "a stream keeps the fetch size of streams"
            recorder.fetchSizes.clear()
            def streamed = context.getBean(TransactionOperations).executeRead { status ->
                repository.findByIdGreaterThan(0L).withCloseable { it.count() }
            }

        then:
            streamed == 5
            recorder.fetchSizes == [50]

        cleanup:
            context?.close()
    }

    private ApplicationContext startContext(Map<String, Object> extraProperties) {
        return ApplicationContext.run([
                'fetch-size-spec'                    : true,
                'datasources.default.url'            : "jdbc:h2:mem:fetchsize${System.nanoTime()};LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
                'datasources.default.driverClassName': "org.h2.Driver",
                'datasources.default.username'       : '',
                'datasources.default.password'       : '',
                'datasources.default.dialect'        : 'h2',
                'datasources.default.schema-generate': SchemaGenerate.CREATE.name(),
                'datasources.default.packages'       : getClass().package.name
        ] + extraProperties)
    }
}

@MappedEntity("fetch_size_note")
class FetchSizeNote {
    @Id
    @GeneratedValue
    Long id
    String text
}

@JdbcRepository(dialect = Dialect.H2)
interface FetchSizeNoteRepository extends CrudRepository<FetchSizeNote, Long> {

    io.micronaut.data.model.Page<FetchSizeNote> findAll(Pageable pageable)

    @Fetch(50)
    Stream<FetchSizeNote> findByIdGreaterThan(Long id)
}

/**
 * Records the fetch sizes set on the prepared statements of the data source.
 */
@Requires(property = "fetch-size-spec")
@Singleton
class FetchSizeRecorder implements BeanCreatedEventListener<DataSource> {

    final List<Integer> fetchSizes = new CopyOnWriteArrayList<>()

    @Override
    DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        return proxy(DataSource, event.bean) { Method method, Object[] args, Object result ->
            method.name == "getConnection" ? recordFetchSizes(result as Connection) : result
        }
    }

    private Connection recordFetchSizes(Connection connection) {
        return proxy(Connection, connection) { Method method, Object[] args, Object result ->
            if (method.name == "prepareStatement") {
                return proxy(PreparedStatement, result as PreparedStatement) { Method statementMethod, Object[] statementArgs, Object statementResult ->
                    if (statementMethod.name == "setFetchSize") {
                        fetchSizes.add(statementArgs[0] as Integer)
                    }
                    return statementResult
                }
            }
            return result
        }
    }

    private static <T> T proxy(Class<T> type, T delegate, Closure<Object> handler) {
        return (T) Proxy.newProxyInstance(FetchSizeRecorder.classLoader, [type] as Class[], { Object proxy, Method method, Object[] args ->
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
