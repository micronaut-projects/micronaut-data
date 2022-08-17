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
package io.micronaut.data.jdbc


import io.micronaut.context.BeanContext
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.jdbc.operations.JdbcSchemaHandler
import io.micronaut.data.tck.tests.AbstractMultitenancySpec
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.transaction.jdbc.DelegatingDataSource

import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

abstract class AbstractJdbcMultitenancySpec extends AbstractMultitenancySpec {

    @Override
    String sourcePrefix() {
        return "datasources"
    }

    @Override
    protected long getDataSourceBooksCount(BeanContext beanContext, String ds) {
        return getBooksCount(beanContext.getBean(DataSource, Qualifiers.byName(ds)))
    }

    @Override
    protected long getSchemaBooksCount(BeanContext beanContext, String schemaName) {
        DataJdbcConfiguration conf = beanContext.getBean(DataJdbcConfiguration)
        JdbcSchemaHandler schemaHandler = beanContext.getBean(JdbcSchemaHandler)
        DataSource dataSource = beanContext.getBean(DataSource)
        if (dataSource instanceof DelegatingDataSource) {
            dataSource = ((DelegatingDataSource) dataSource).getTargetDataSource();
        }
        try (Connection connection = dataSource.getConnection()) {
            schemaHandler.useSchema(connection, conf.dialect, schemaName)
            try (PreparedStatement ps = connection.prepareStatement("select count(*) from book")) {
                try (ResultSet resultSet = ps.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        }
    }

    private long getBooksCount(DataSource ds) throws SQLException {
        if (ds instanceof DelegatingDataSource) {
            ds = ((DelegatingDataSource) ds).getTargetDataSource()
        }
        try (Connection connection = ds.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement("select count(*) from book")) {
                try (ResultSet resultSet = ps.executeQuery()) {
                    resultSet.next()
                    return resultSet.getLong(1)
                }
            }
        }
    }

}
