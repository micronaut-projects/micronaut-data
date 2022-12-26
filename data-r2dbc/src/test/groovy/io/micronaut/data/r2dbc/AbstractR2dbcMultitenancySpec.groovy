/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.r2dbc


import io.micronaut.context.BeanContext
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration
import io.micronaut.data.r2dbc.operations.R2dbcSchemaHandler
import io.micronaut.data.tck.tests.AbstractMultitenancySpec
import io.micronaut.inject.qualifiers.Qualifiers
import io.r2dbc.spi.ConnectionFactory
import reactor.core.publisher.Mono

import java.sql.SQLException
import java.util.function.Function

abstract class AbstractR2dbcMultitenancySpec extends AbstractMultitenancySpec {

    @Override
    String sourcePrefix() {
        return "r2dbc.datasources"
    }

    @Override
    protected long getDataSourceBooksCount(BeanContext beanContext, String ds) {
        return getBooksCount(beanContext.getBean(ConnectionFactory, Qualifiers.byName(ds)))
    }

    @Override
    protected long getSchemaBooksCount(BeanContext beanContext, String schemaName) {
        DataR2dbcConfiguration conf = beanContext.getBean(DataR2dbcConfiguration)
        def dialect = conf.getDialect()
        R2dbcSchemaHandler schemaHandler = beanContext.getBean(R2dbcSchemaHandler)
        ConnectionFactory cf = beanContext.getBean(ConnectionFactory)
        return Mono.from(cf.create())
                .flatMap(c -> Mono.from(schemaHandler.useSchema(c, dialect, schemaName)).thenReturn(c))
                .flatMap(c -> Mono.from(c.createStatement("select count(*) from book").execute()))
                .flatMap(r -> Mono.from(r.<Long>map({read -> read.get(0) as Long} as Function)))
                .block()
    }

    private long getBooksCount(ConnectionFactory cf) throws SQLException {
        return Mono.from(cf.create())
                .flatMap(c -> Mono.from(c.createStatement("select count(*) from book").execute()))
                .flatMap(r -> Mono.from(r.<Long>map({read -> read.get(0) as Long} as Function)))
                .block()
    }

}
