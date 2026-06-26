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
package io.micronaut.data.jdbc.operations

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.util.logging.Logger

class JdbcRepositoryOperationsConditionsSpec extends Specification {

    void "default operations are selected for non-special dialect #dialect"() {
        given:
        ApplicationContext context = contextWithDataSource('default', dialect)

        expect:
        context.getBean(JdbcRepositoryOperations, Qualifiers.byName('default')) instanceof DefaultJdbcRepositoryOperations

        cleanup:
        context.close()

        where:
        dialect << ['H2', 'MYSQL']
    }

    void "oracle operations are selected for oracle dialect #dialect"() {
        given:
        ApplicationContext context = contextWithDataSource('default', dialect)

        expect:
        context.getBean(JdbcRepositoryOperations, Qualifiers.byName('default')) instanceof OracleJdbcRepositoryOperations

        cleanup:
        context.close()

        where:
        dialect << ['ORACLE', 'oracle']
    }

    void "sql server operations are selected for sql server dialect #dialect"() {
        given:
        ApplicationContext context = contextWithDataSource('default', dialect)

        expect:
        context.getBean(JdbcRepositoryOperations, Qualifiers.byName('default')) instanceof SqlServerJdbcRepositoryOperations

        cleanup:
        context.close()

        where:
        dialect << ['SQL_SERVER', 'sql_server']
    }

    void "operations condition uses named datasource dialect #dialect"() {
        given:
        ApplicationContext context = applicationContextBuilder([
                'datasources.default.dialect': 'H2',
                'datasources.default.enabled': true,
                'datasources.mdb.enabled'    : true,
                'datasources.mdb.dialect'    : dialect
        ])
        context.start()

        expect:
        context.getBean(JdbcRepositoryOperations, Qualifiers.byName('default')) instanceof DefaultJdbcRepositoryOperations
        context.getBean(JdbcRepositoryOperations, Qualifiers.byName('mdb')).class == operationsType

        cleanup:
        context.close()

        where:
        dialect      | operationsType
        'ORACLE'     | OracleJdbcRepositoryOperations
        'SQL_SERVER' | SqlServerJdbcRepositoryOperations
    }

    void "default operations are selected for named non-special datasource when default is #specialDialect"() {
        given:
        ApplicationContext context = applicationContextBuilder([
                'datasources.default.dialect': specialDialect,
                'datasources.default.enabled': true,
                'datasources.mdb.enabled'    : true,
                'datasources.mdb.dialect'    : 'H2'
        ])
        context.start()

        expect:
        context.getBean(JdbcRepositoryOperations, Qualifiers.byName('default')).class == specialOperationsType
        context.getBean(JdbcRepositoryOperations, Qualifiers.byName('mdb')) instanceof DefaultJdbcRepositoryOperations

        cleanup:
        context.close()

        where:
        specialDialect | specialOperationsType
        'ORACLE'       | OracleJdbcRepositoryOperations
        'SQL_SERVER'   | SqlServerJdbcRepositoryOperations
    }

    private ApplicationContext contextWithDataSource(String dataSourceName, String dialect) {
        ApplicationContext context = applicationContextBuilder([
                ('datasources.' + dataSourceName + '.enabled'): true,
                ('datasources.' + dataSourceName + '.dialect') : dialect
        ])
        context.start()
        return context
    }

    private ApplicationContext applicationContextBuilder(Map<String, Object> properties) {
        return ApplicationContext.builder(properties + [
                'micronaut.test.resources.enabled'                      : false,
                'jdbc.repository.operations.conditions.stub-datasources': true
        ]).beansPredicate(beanType -> {
            if (beanType instanceof BeanDefinitionReference) {
                String beanDefinitionName = beanType.beanDefinitionName
                return !beanDefinitionName.contains('io.micronaut.configuration.jdbc.hikari.$DatasourceFactory')
                        && !beanDefinitionName.contains('io.micronaut.configuration.jdbc.tomcat.$DatasourceFactory')
            }
            return true
        }).build()
    }

    @Factory
    @Requires(property = 'jdbc.repository.operations.conditions.stub-datasources', value = 'true')
    static class StubDataSourceFactory {

        @Singleton
        @Named('default')
        @Requires(property = 'datasources.default.enabled', value = 'true')
        DataSource defaultDataSource() {
            return new StubDataSource()
        }

        @Singleton
        @Named('mdb')
        @Requires(property = 'datasources.mdb.enabled', value = 'true')
        DataSource mdbDataSource() {
            return new StubDataSource()
        }
    }

    private static final class StubDataSource implements DataSource {

        @Override
        Connection getConnection() throws SQLException {
            throw new SQLFeatureNotSupportedException()
        }

        @Override
        Connection getConnection(String username, String password) throws SQLException {
            throw new SQLFeatureNotSupportedException()
        }

        @Override
        PrintWriter getLogWriter() throws SQLException {
            return null
        }

        @Override
        void setLogWriter(PrintWriter out) throws SQLException {
        }

        @Override
        void setLoginTimeout(int seconds) throws SQLException {
        }

        @Override
        int getLoginTimeout() throws SQLException {
            return 0
        }

        @Override
        Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException()
        }

        @Override
        <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLFeatureNotSupportedException()
        }

        @Override
        boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false
        }
    }
}
