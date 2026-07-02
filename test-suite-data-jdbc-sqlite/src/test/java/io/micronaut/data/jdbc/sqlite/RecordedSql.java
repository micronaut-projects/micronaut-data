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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

final class RecordedSql {
    private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    private RecordedSql() {
    }

    static void clear() {
        STATEMENTS.clear();
    }

    static List<String> statements() {
        return List.copyOf(STATEMENTS);
    }

    static boolean hasStatementContaining(String operation, String clause) {
        String expectedOperation = operation.toUpperCase(Locale.ENGLISH);
        String expectedClause = clause.toUpperCase(Locale.ENGLISH);
        return statements().stream()
            .map(sql -> sql.toUpperCase(Locale.ENGLISH))
            .anyMatch(sql -> sql.contains(expectedOperation) && sql.contains(expectedClause));
    }

    static void add(String sql) {
        STATEMENTS.add(sql);
    }

    static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}

@Singleton
@Order(Ordered.HIGHEST_PRECEDENCE)
@Requires(property = "sqlite.sql-recorder.enabled", value = "true")
final class RecordedSqlDataSourceListener implements BeanCreatedEventListener<DataSource> {

    @Override
    public DataSource onCreated(BeanCreatedEvent<DataSource> event) {
        DataSource dataSource = event.getBean();
        if (dataSource instanceof DelegatingDataSource) {
            return dataSource;
        }
        return (DataSource) Proxy.newProxyInstance(
            dataSource.getClass().getClassLoader(),
            new Class<?>[]{DataSource.class},
            new DataSourceInvocationHandler(dataSource)
        );
    }

    private record DataSourceInvocationHandler(DataSource target) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getConnection")) {
                Connection connection = (Connection) RecordedSql.invoke(method, target, args);
                return Proxy.newProxyInstance(
                    connection.getClass().getClassLoader(),
                    new Class<?>[]{Connection.class},
                    new ConnectionInvocationHandler(connection)
                );
            }
            return RecordedSql.invoke(method, target, args);
        }
    }

    private record ConnectionInvocationHandler(Connection target) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("prepareStatement") && args != null && args.length > 0 && args[0] instanceof String sql) {
                RecordedSql.add(sql);
            }
            return RecordedSql.invoke(method, target, args);
        }
    }
}
