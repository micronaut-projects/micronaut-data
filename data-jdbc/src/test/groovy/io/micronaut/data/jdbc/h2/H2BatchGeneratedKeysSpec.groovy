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
package io.micronaut.data.jdbc.h2

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * The generated keys of a batch insert are matched to the entities by position, so there must be exactly one per
 * entity.
 */
class H2BatchGeneratedKeysSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ["spec.name": "H2BatchGeneratedKeysSpec"])

    @Shared
    H2BatchGeneratedKeysEntityRepository repository = context.getBean(H2BatchGeneratedKeysEntityRepository)

    void cleanup() {
        GeneratedKeysTamperer.repeatFirstKey = false
    }

    void "assigns each generated key to its own entity"() {
        when:
        List<H2BatchGeneratedKeysEntity> saved = repository.saveAll(entities("a", "b", "c"))

        then:
        saved.collect { repository.findById(it.id).get().name } == ["a", "b", "c"]
    }

    void "fails when the database returns more generated keys than entities"() {
        given:
        GeneratedKeysTamperer.repeatFirstKey = true

        when:
        repository.saveAll(entities("a", "b", "c"))

        then:
        def e = thrown(DataAccessException)
        e.message.contains("3")
        e.message.contains("4")
    }

    private static List<H2BatchGeneratedKeysEntity> entities(String... names) {
        return names.collect { new H2BatchGeneratedKeysEntity(name: it) }
    }
}

@MappedEntity("h2_batch_generated_keys")
class H2BatchGeneratedKeysEntity {
    @Id
    @GeneratedValue
    Long id
    String name
}

@JdbcRepository(dialect = Dialect.H2)
interface H2BatchGeneratedKeysEntityRepository extends CrudRepository<H2BatchGeneratedKeysEntity, Long> {
}

/**
 * Repeats the first row of the generated keys of a statement prepared to return them.
 */
@Singleton
@Requires(property = "spec.name", value = "H2BatchGeneratedKeysSpec")
class GeneratedKeysTamperer implements BeanCreatedEventListener<DataSource> {

    static volatile boolean repeatFirstKey

    @Override
    DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        return proxy(DataSource, event.bean) { Method method, Object result ->
            method.name == "getConnection" ? connection((Connection) result) : result
        }
    }

    private static Connection connection(Connection connection) {
        return proxy(Connection, connection) { Method method, Object result ->
            method.name == "prepareStatement" ? statement((PreparedStatement) result) : result
        }
    }

    private static PreparedStatement statement(PreparedStatement statement) {
        return proxy(PreparedStatement, statement) { Method method, Object result ->
            method.name == "getGeneratedKeys" && repeatFirstKey ? repeatFirstRow((ResultSet) result) : result
        }
    }

    private static ResultSet repeatFirstRow(ResultSet resultSet) {
        int nextCalls = 0
        return (ResultSet) Proxy.newProxyInstance(ResultSet.classLoader, [ResultSet] as Class[]) { Object p, Method method, Object[] args ->
            if (method.name == "next" && ++nextCalls == 2) {
                // Stay on the first row, so it is read twice
                return true
            }
            return invoke(resultSet, method, args)
        }
    }

    private static <T> T proxy(Class<T> type, T delegate, Closure<Object> afterCall) {
        return (T) Proxy.newProxyInstance(type.classLoader, [type] as Class[]) { Object p, Method method, Object[] args ->
            return afterCall.call(method, invoke(delegate, method, args))
        }
    }

    private static Object invoke(Object delegate, Method method, Object[] args) {
        try {
            return method.invoke(delegate, args)
        } catch (InvocationTargetException e) {
            throw e.cause
        }
    }
}
