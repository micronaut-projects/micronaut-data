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
package io.micronaut.data.jdbc.h2.batch

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
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
import java.sql.Statement

/**
 * Batch results with {@link Statement#SUCCESS_NO_INFO} or {@link Statement#EXECUTE_FAILED} instead of row counts.
 */
class H2BatchCountSpec extends Specification implements H2TestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared
    BatchItemRepository repository = ctx.getBean(BatchItemRepository)

    @Shared
    VersionedBatchItemRepository versionedRepository = ctx.getBean(VersionedBatchItemRepository)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    @Override
    Map<String, String> getProperties() {
        return H2TestPropertyProvider.super.getProperties() + [
            'datasources.default.url': 'jdbc:h2:mem:batchCount;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE',
            'test.batch-counts'      : 'true'
        ]
    }

    def cleanup() {
        BatchCountOverride.reportedCount = null
        repository.deleteAll()
    }

    void "a batch insert succeeds when the driver reports no row counts"() {
        given:
            BatchCountOverride.reportedCount = Statement.SUCCESS_NO_INFO
        when:
            def saved = repository.saveAll([new BatchItem(name: "a"), new BatchItem(name: "b")])
        then:
            saved*.id.every { it != null }
            repository.count() == 2
    }

    void "a batch update without a version succeeds when the driver reports no row counts"() {
        given:
            def saved = repository.saveAll([new BatchItem(name: "a"), new BatchItem(name: "b")])
            BatchCountOverride.reportedCount = Statement.SUCCESS_NO_INFO
        when:
            saved.each { it.name = it.name + "!" }
            repository.updateAll(saved)
        then:
            repository.findAll()*.name.sort() == ["a!", "b!"]
    }

    void "a versioned batch update fails clearly when the driver reports no row counts"() {
        given:
            def saved = versionedRepository.saveAll([new VersionedBatchItem(name: "a"), new VersionedBatchItem(name: "b")])
            BatchCountOverride.reportedCount = Statement.SUCCESS_NO_INFO
        when:
            saved.each { it.name = it.name + "!" }
            versionedRepository.updateAll(saved)
        then:
            def e = thrown(OptimisticLockException)
            e.message.contains("SUCCESS_NO_INFO")
        cleanup:
            BatchCountOverride.reportedCount = null
            versionedRepository.deleteAll()
    }

    void "a batch fails when the driver reports a failed statement"() {
        given:
            BatchCountOverride.reportedCount = Statement.EXECUTE_FAILED
        when:
            repository.saveAll([new BatchItem(name: "a"), new BatchItem(name: "b")])
        then:
            thrown(DataAccessException)
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface BatchItemRepository extends CrudRepository<BatchItem, Long> {
}

@JdbcRepository(dialect = Dialect.H2)
interface VersionedBatchItemRepository extends CrudRepository<VersionedBatchItem, Long> {
}

@MappedEntity
class VersionedBatchItem {
    @Id
    @GeneratedValue
    Long id

    String name

    @Version
    Long version
}

@MappedEntity
class BatchItem {
    @Id
    @GeneratedValue
    Long id

    String name
}

class BatchCountOverride {
    static volatile Integer reportedCount
}

@Singleton
@Requires(property = "test.batch-counts", value = "true")
class BatchCountDataSourceListener implements BeanCreatedEventListener<DataSource> {

    @Override
    DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        DataSource dataSource = event.bean
        return proxy(DataSource) { Method method, Object[] args ->
            Object result = invoke(dataSource, method, args)
            method.name == "getConnection" ? overrideConnection((Connection) result) : result
        }
    }

    private static Connection overrideConnection(Connection connection) {
        return proxy(Connection) { Method method, Object[] args ->
            Object result = invoke(connection, method, args)
            method.name == "prepareStatement" && result instanceof PreparedStatement ? overrideStatement((PreparedStatement) result) : result
        }
    }

    private static PreparedStatement overrideStatement(PreparedStatement statement) {
        return proxy(PreparedStatement) { Method method, Object[] args ->
            Object result = invoke(statement, method, args)
            Integer reportedCount = BatchCountOverride.reportedCount
            if (method.name == "executeBatch" && reportedCount != null) {
                int[] counts = (int[]) result
                Arrays.fill(counts, reportedCount)
                return counts
            }
            result
        }
    }

    private static <T> T proxy(Class<T> type, Closure<Object> handler) {
        return (T) Proxy.newProxyInstance(BatchCountDataSourceListener.classLoader, [type] as Class[],
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
