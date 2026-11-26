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
package io.micronaut.data.jdbc.calls

import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton

import javax.sql.DataSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.CallableStatement
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Counts the calls the JDBC operations make on the driver, for tests that pin how many statements an operation
 * prepares and executes.
 *
 * <p>Enable it with the {@value #ENABLED_PROPERTY} property: every {@link DataSource} bean of the context is then
 * wrapped, and every call on the connections and statements it returns is counted by {@code Interface.method}, for
 * example {@code Connection.prepareStatement} or {@code PreparedStatement.executeBatch}. The SQL of every prepared or
 * created statement is recorded in order.</p>
 *
 * <p>A count is a number of driver calls, not a number of network round trips: whether a call reaches the server
 * depends on the driver.</p>
 *
 * <pre>{@code
 * ApplicationContext ctx = ApplicationContext.run(properties + [(DriverCallCounter.ENABLED_PROPERTY): 'true'])
 * DriverCallCounter counter = ctx.getBean(DriverCallCounter)
 * counter.reset()
 * repository.findById(id)
 * counter.prepares() == 1
 * }</pre>
 */
@Singleton
@Requires(property = DriverCallCounter.ENABLED_PROPERTY, value = "true")
class DriverCallCounter implements BeanCreatedEventListener<DataSource> {

    static final String ENABLED_PROPERTY = "test.driver-call-counter"

    static final String GET_CONNECTION = "DataSource.getConnection"

    private static final Set<String> PREPARE_METHODS = Set.of("prepareStatement", "prepareCall", "createStatement")
    private static final Set<String> EXECUTE_METHODS = Set.of(
        "execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch", "executeLargeBatch"
    )

    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>()
    private final List<String> statements = new CopyOnWriteArrayList<>()

    @Override
    DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        DataSource dataSource = event.bean
        return proxy(DataSource, dataSource, "DataSource") { Method method, Object result ->
            method.name == "getConnection" ? wrapConnection((Connection) result) : result
        }
    }

    /**
     * Forgets everything counted so far.
     */
    void reset() {
        calls.clear()
        statements.clear()
    }

    /**
     * @param call The call, as {@code Interface.method}, for example {@code Connection.commit}
     * @return How many times it was made since the last reset
     */
    int count(String call) {
        return calls.get(call)?.get() ?: 0
    }

    /**
     * @return The connections obtained from the data source
     */
    int connections() {
        return count(GET_CONNECTION)
    }

    /**
     * @return The statements prepared or created on a connection
     */
    int prepares() {
        return PREPARE_METHODS.sum { count("Connection." + it) } as int
    }

    /**
     * @return The statements executed, a batch counting once
     */
    int executes() {
        return calls.findAll { call, n -> isStatement(call) && EXECUTE_METHODS.contains(method(call)) }
            .values().sum(0) { it.get() } as int
    }

    /**
     * @return The rows added to a batch
     */
    int batchedRows() {
        return calls.findAll { call, n -> isStatement(call) && method(call) == "addBatch" }
            .values().sum(0) { it.get() } as int
    }

    /**
     * @return The SQL of the statements prepared or created, in order
     */
    List<String> statements() {
        return List.copyOf(statements)
    }

    /**
     * @return Every call counted since the last reset, sorted by name
     */
    Map<String, Integer> snapshot() {
        return new TreeMap<String, Integer>(calls.collectEntries { call, n -> [(call): n.get()] })
    }

    /**
     * @return The calls on connections and data sources, sorted by name, without the statement calls
     */
    Map<String, Integer> connectionCalls() {
        return snapshot().findAll { call, n -> !isStatement(call) } as Map<String, Integer>
    }

    private static boolean isStatement(String call) {
        return call.startsWith("Statement.") || call.startsWith("PreparedStatement.") || call.startsWith("CallableStatement.")
    }

    private static String method(String call) {
        return call.substring(call.indexOf('.') + 1)
    }

    private Connection wrapConnection(Connection connection) {
        // The statements a connection returns are wrapped by proxy()
        return proxy(Connection, connection, "Connection") { Method method, Object result -> result }
    }

    /**
     * Wraps the target so that every call is counted as {@code name.method}, and every statement it returns is
     * wrapped as well.
     */
    private <T> T proxy(Class<T> type, T target, String name, Closure<Object> wrapResult) {
        return (T) Proxy.newProxyInstance(DriverCallCounter.classLoader, [type] as Class[], { Object self, Method method, Object[] args ->
            switch (method.name) {
                case "equals":
                    return self.is(args[0])
                case "hashCode":
                    return System.identityHashCode(self)
                case "toString":
                    return "Counted" + name + "[" + target + "]"
            }
            calls.computeIfAbsent(name + "." + method.name) { new AtomicInteger() }.incrementAndGet()
            if (name == "Connection" && PREPARE_METHODS.contains(method.name) && args && args[0] instanceof String) {
                statements.add((String) args[0])
            }
            Object result
            try {
                result = method.invoke(target, args)
            } catch (InvocationTargetException e) {
                throw e.cause
            }
            if (result instanceof CallableStatement) {
                return proxy(CallableStatement, (CallableStatement) result, "CallableStatement") { Method m, Object r -> r }
            }
            if (result instanceof PreparedStatement) {
                return proxy(PreparedStatement, (PreparedStatement) result, "PreparedStatement") { Method m, Object r -> r }
            }
            if (result instanceof Statement) {
                return proxy(Statement, (Statement) result, "Statement") { Method m, Object r -> r }
            }
            return wrapResult.call(method, result)
        } as InvocationHandler)
    }
}
