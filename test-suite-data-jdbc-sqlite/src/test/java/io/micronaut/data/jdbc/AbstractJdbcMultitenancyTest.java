package io.micronaut.data.jdbc;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.jdbc.config.DataJdbcConfiguration;
import io.micronaut.data.jdbc.operations.JdbcSchemaHandler;
import io.micronaut.data.tck.tests.AbstractMultitenancySpec;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

abstract class AbstractJdbcMultitenancyTest extends AbstractMultitenancySpec {

    @Override
    public String sourcePrefix() {
        return "datasources";
    }

    @Override
    public long countDataSources(ApplicationContext context) {
        return context.getBeansOfType(DataSource.class).size();
    }

    @Override
    protected long getDataSourceBooksCount(BeanContext beanContext, String ds) {
        return getBooksCount(beanContext.getBean(DataSource.class, Qualifiers.byName(ds)));
    }

    @Override
    protected long getSchemaBooksCount(BeanContext beanContext, String schemaName) {
        DataJdbcConfiguration conf = beanContext.getBean(DataJdbcConfiguration.class);
        JdbcSchemaHandler schemaHandler = beanContext.getBean(JdbcSchemaHandler.class);
        DataSource dataSource = beanContext.getBean(DataSource.class);
        if (dataSource instanceof DelegatingDataSource delegatingDataSource) {
            dataSource = delegatingDataSource.getTargetDataSource();
        }
        try (Connection connection = dataSource.getConnection()) {
            schemaHandler.useSchema(connection, conf.getDialect(), schemaName);
            try (PreparedStatement ps = connection.prepareStatement("select count(*) from book")) {
                try (ResultSet resultSet = ps.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private long getBooksCount(DataSource ds) {
        if (ds instanceof DelegatingDataSource delegatingDataSource) {
            ds = delegatingDataSource.getTargetDataSource();
        }
        try (Connection connection = ds.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement("select count(*) from book")) {
                try (ResultSet resultSet = ps.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
