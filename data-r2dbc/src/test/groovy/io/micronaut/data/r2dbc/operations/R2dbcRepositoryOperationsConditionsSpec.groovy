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
package io.micronaut.data.r2dbc.operations

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.qualifiers.Qualifiers
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification

class R2dbcRepositoryOperationsConditionsSpec extends Specification {

    void "default operations are selected for non-special dialect #dialect"() {
        given:
        ApplicationContext context = contextWithConnectionFactory('default', dialect)

        expect:
        context.getBean(R2dbcOperations, Qualifiers.byName('default')) instanceof DefaultR2dbcRepositoryOperations

        cleanup:
        context.close()

        where:
        dialect << ['H2', 'MYSQL']
    }

    void "oracle operations are selected for oracle dialect #dialect"() {
        given:
        ApplicationContext context = contextWithConnectionFactory('default', dialect)

        expect:
        context.getBean(R2dbcOperations, Qualifiers.byName('default')) instanceof OracleR2dbcRepositoryOperations

        cleanup:
        context.close()

        where:
        dialect << ['ORACLE', 'oracle']
    }

    void "sql server operations are selected for sql server dialect #dialect"() {
        given:
        ApplicationContext context = contextWithConnectionFactory('default', dialect)

        expect:
        context.getBean(R2dbcOperations, Qualifiers.byName('default')) instanceof SqlServerR2dbcRepositoryOperations

        cleanup:
        context.close()

        where:
        dialect << ['SQL_SERVER', 'sql_server']
    }

    void "operations condition uses named datasource dialect #dialect"() {
        given:
        ApplicationContext context = applicationContextBuilder([
                'r2dbc.datasources.default.dialect': 'H2',
                'r2dbc.datasources.default.enabled': true,
                'r2dbc.datasources.mdb.enabled'    : true,
                'r2dbc.datasources.mdb.dialect'    : dialect
        ])
        context.start()

        expect:
        context.getBean(R2dbcOperations, Qualifiers.byName('default')) instanceof DefaultR2dbcRepositoryOperations
        context.getBean(R2dbcOperations, Qualifiers.byName('mdb')).class == operationsType

        cleanup:
        context.close()

        where:
        dialect      | operationsType
        'ORACLE'     | OracleR2dbcRepositoryOperations
        'SQL_SERVER' | SqlServerR2dbcRepositoryOperations
    }

    void "default operations are selected for named non-special datasource when default is #specialDialect"() {
        given:
        ApplicationContext context = applicationContextBuilder([
                'r2dbc.datasources.default.dialect': specialDialect,
                'r2dbc.datasources.default.enabled': true,
                'r2dbc.datasources.mdb.enabled'    : true,
                'r2dbc.datasources.mdb.dialect'    : 'H2'
        ])
        context.start()

        expect:
        context.getBean(R2dbcOperations, Qualifiers.byName('default')).class == specialOperationsType
        context.getBean(R2dbcOperations, Qualifiers.byName('mdb')) instanceof DefaultR2dbcRepositoryOperations

        cleanup:
        context.close()

        where:
        specialDialect | specialOperationsType
        'ORACLE'       | OracleR2dbcRepositoryOperations
        'SQL_SERVER'   | SqlServerR2dbcRepositoryOperations
    }

    private ApplicationContext contextWithConnectionFactory(String dataSourceName, String dialect) {
        ApplicationContext context = applicationContextBuilder([
                ('r2dbc.datasources.' + dataSourceName + '.enabled'): true,
                ('r2dbc.datasources.' + dataSourceName + '.dialect') : dialect
        ])
        context.start()
        return context
    }

    private ApplicationContext applicationContextBuilder(Map<String, Object> properties) {
        return ApplicationContext.builder(properties + [
                'micronaut.test.resources.enabled'                   : false,
                'r2dbc.repository.operations.conditions.stub-factories': true
        ]).beansPredicate(beanType -> {
            if (beanType instanceof BeanDefinitionReference) {
                String beanDefinitionName = beanType.beanDefinitionName
                return !beanDefinitionName.contains('io.micronaut.r2dbc.$DefaultBasicR2dbcProperties')
                    && !beanDefinitionName.contains('io.micronaut.r2dbc.$R2dbcConnectionFactoryBean')
            }
            return true
        }).build()
    }

    @Factory
    @Requires(property = 'r2dbc.repository.operations.conditions.stub-factories', value = 'true')
    static class StubConnectionFactoryFactory {

        @Singleton
        @Named('default')
        @Requires(property = 'r2dbc.datasources.default.enabled', value = 'true')
        ConnectionFactory defaultConnectionFactory() {
            return new StubConnectionFactory()
        }

        @Singleton
        @Named('mdb')
        @Requires(property = 'r2dbc.datasources.mdb.enabled', value = 'true')
        ConnectionFactory mdbConnectionFactory() {
            return new StubConnectionFactory()
        }
    }

    private static final class StubConnectionFactory implements ConnectionFactory {

        @Override
        Publisher<? extends Connection> create() {
            return Mono.error(new UnsupportedOperationException())
        }

        @Override
        ConnectionFactoryMetadata getMetadata() {
            return () -> 'stub'
        }
    }
}
