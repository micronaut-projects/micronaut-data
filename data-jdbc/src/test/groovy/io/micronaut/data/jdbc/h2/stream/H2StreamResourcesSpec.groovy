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
package io.micronaut.data.jdbc.h2.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.runtime.convert.AttributeConverter
import io.micronaut.data.repository.CrudRepository
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream

/**
 * A streamed query must close its statement when it fails and when closing an earlier resource fails.
 */
class H2StreamResourcesSpec extends Specification implements H2TestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared
    StreamItemRepository repository = ctx.getBean(StreamItemRepository)

    @Shared
    StreamParentRepository parentRepository = ctx.getBean(StreamParentRepository)

    @Shared
    StreamChildRepository childRepository = ctx.getBean(StreamChildRepository)

    @Shared
    ConnectionOperations<Connection> connectionOperations = ctx.getBean(ConnectionOperations)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    @Override
    Map<String, String> getProperties() {
        return H2TestPropertyProvider.super.getProperties() + [
            'datasources.default.url': 'jdbc:h2:mem:streamResources;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
            'test.track-statements'  : 'true'
        ]
    }

    def setup() {
        repository.saveAll([new StreamItem(name: "a", code: new ItemCode("x")), new StreamItem(name: "b", code: new ItemCode("y"))])
        StatementTracker.reset()
    }

    def cleanup() {
        StatementTracker.reset()
        repository.deleteAll()
    }

    void "a stream that is read to the end closes its statement"() {
        when:
            def names = connectionOperations.executeRead { repository.queryAll().map { it.name }.toList() }
        then:
            names.toSorted() == ["a", "b"]
            StatementTracker.openStatements.get() == 0
    }

    void "a stream whose parameters fail to bind closes its statement"() {
        when:
            connectionOperations.executeRead { repository.findByCode(new ItemCode(ItemCodeConverter.FAILING)).toList() }
        then:
            thrown(DataAccessException)
            StatementTracker.openStatements.get() == 0
    }

    void "the statement is closed when closing the result set fails"() {
        given:
            StatementTracker.failResultSetClose = true
        when:
            connectionOperations.executeRead { repository.queryAll().toList() }
        then:
            def e = thrown(DataAccessException)
            e.message.contains("Error closing JDBC result stream")
            StatementTracker.openStatements.get() == 0
    }

    void "a stream whose parameter binding throws an Error closes its statement"() {
        when:
            connectionOperations.executeRead { repository.findByCode(new ItemCode(ItemCodeConverter.ERROR_ON_BIND)).toList() }
        then:
            thrown(AssertionError)
            StatementTracker.openStatements.get() == 0
    }

    void "a fetch-joined stream closes its statement when mapping throws an Error"() {
        given:
            def parent = parentRepository.save(new StreamParent(name: "p", code: new ItemCode(ItemCodeConverter.ERROR_ON_READ)))
            childRepository.save(new StreamChild(name: "c", parent: parent))
            StatementTracker.reset()
        when:
            connectionOperations.executeRead { parentRepository.queryByName("p").toList() }
        then:
            thrown(AssertionError)
            StatementTracker.openStatements.get() == 0
        cleanup:
            childRepository.deleteAll()
            parentRepository.deleteAll()
    }

    void "a stream closes its statement when mapping a row throws, even if the stream isn't closed"() {
        given:
            repository.save(new StreamItem(name: "c", code: new ItemCode(ItemCodeConverter.ERROR_ON_READ)))
            StatementTracker.reset()
        when:
            connectionOperations.executeRead { repository.queryAll().iterator().forEachRemaining {} }
        then:
            thrown(AssertionError)
            StatementTracker.openStatements.get() == 0
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface StreamItemRepository extends CrudRepository<StreamItem, Long> {

    Stream<StreamItem> queryAll()

    Stream<StreamItem> findByCode(ItemCode code)
}

@JdbcRepository(dialect = Dialect.H2)
interface StreamParentRepository extends CrudRepository<StreamParent, Long> {

    @Join(value = "children", type = Join.Type.LEFT_FETCH)
    Stream<StreamParent> queryByName(String name)
}

@JdbcRepository(dialect = Dialect.H2)
interface StreamChildRepository extends CrudRepository<StreamChild, Long> {
}

@MappedEntity
class StreamParent {
    @Id
    @GeneratedValue
    Long id

    String name

    @TypeDef(type = DataType.STRING, converter = ItemCodeConverter)
    ItemCode code

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "parent")
    List<StreamChild> children
}

@MappedEntity
class StreamChild {
    @Id
    @GeneratedValue
    Long id

    String name

    @Relation(Relation.Kind.MANY_TO_ONE)
    StreamParent parent
}

@MappedEntity
class StreamItem {
    @Id
    @GeneratedValue
    Long id

    String name

    @TypeDef(type = DataType.STRING, converter = ItemCodeConverter)
    ItemCode code
}

class ItemCode {
    String value

    ItemCode(String value) {
        this.value = value
    }
}

@Singleton
class ItemCodeConverter implements AttributeConverter<ItemCode, String> {

    static final String FAILING = "fail-to-bind"
    static final String ERROR_ON_BIND = "error-on-bind"
    static final String ERROR_ON_READ = "error-on-read"

    @Override
    String convertToPersistedValue(ItemCode entityValue, ConversionContext context) {
        if (entityValue?.value == FAILING) {
            throw new IllegalStateException("Cannot bind " + FAILING)
        }
        if (entityValue?.value == ERROR_ON_BIND) {
            throw new AssertionError("Cannot bind " + ERROR_ON_BIND)
        }
        return entityValue?.value
    }

    @Override
    ItemCode convertToEntityValue(String persistedValue, ConversionContext context) {
        if (persistedValue == ERROR_ON_READ) {
            throw new AssertionError("Cannot read " + ERROR_ON_READ)
        }
        return persistedValue == null ? null : new ItemCode(persistedValue)
    }
}

class StatementTracker {
    static final AtomicInteger openStatements = new AtomicInteger()
    static volatile boolean failResultSetClose

    static void reset() {
        openStatements.set(0)
        failResultSetClose = false
    }
}

@Singleton
@Requires(property = "test.track-statements", value = "true")
class StatementTrackingDataSourceListener implements BeanCreatedEventListener<DataSource> {

    @Override
    DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        DataSource dataSource = event.bean
        return proxy(DataSource, dataSource) { Method method, Object[] args ->
            Object result = invoke(dataSource, method, args)
            method.name == "getConnection" ? trackConnection((Connection) result) : result
        }
    }

    private static Connection trackConnection(Connection connection) {
        return proxy(Connection, connection) { Method method, Object[] args ->
            Object result = invoke(connection, method, args)
            if (method.name == "prepareStatement" && result instanceof PreparedStatement) {
                StatementTracker.openStatements.incrementAndGet()
                return trackStatement((PreparedStatement) result)
            }
            result
        }
    }

    private static PreparedStatement trackStatement(PreparedStatement statement) {
        boolean[] closed = [false]
        return proxy(PreparedStatement, statement) { Method method, Object[] args ->
            Object result = invoke(statement, method, args)
            if (method.name == "close" && !closed[0]) {
                closed[0] = true
                StatementTracker.openStatements.decrementAndGet()
            }
            if (method.name == "executeQuery" && result instanceof ResultSet) {
                return failingCloseResultSet((ResultSet) result)
            }
            result
        }
    }

    private static ResultSet failingCloseResultSet(ResultSet resultSet) {
        return proxy(ResultSet, resultSet) { Method method, Object[] args ->
            Object result = invoke(resultSet, method, args)
            if (method.name == "close" && StatementTracker.failResultSetClose) {
                throw new SQLException("Result set close failure")
            }
            result
        }
    }

    private static <T> T proxy(Class<T> type, T target, Closure<Object> handler) {
        return (T) Proxy.newProxyInstance(StatementTrackingDataSourceListener.classLoader, [type] as Class[],
            { Object p, Method method, Object[] args -> handler.call(method, args) } as InvocationHandler)
    }

    private static Object invoke(Object target, Method method, Object[] args) {
        try {
            return method.invoke(target, args)
        } catch (InvocationTargetException e) {
            throw e.cause
        }
    }
}
