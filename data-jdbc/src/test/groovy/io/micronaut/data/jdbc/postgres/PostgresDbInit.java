package io.micronaut.data.jdbc.postgres;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.jdbc.BasicJdbcConfiguration;
import io.micronaut.transaction.jdbc.DelegatingDataSource;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

@Context
@Singleton
public class PostgresDbInit implements BeanCreatedEventListener<BasicJdbcConfiguration> {

    @Override
    public BasicJdbcConfiguration onCreated(BeanCreatedEvent<BasicJdbcConfiguration> event) {
        BasicJdbcConfiguration configuration = event.getBean();
        if (!configuration.getConfiguredDriverClassName().toLowerCase(Locale.ROOT).contains("postgres")) {
            return configuration;
        }

        final Properties info = new Properties();
        info.put("user", configuration.getUsername());
        info.put("password", configuration.getPassword());

        try {
            try (Connection connection = DriverManager.getConnection(configuration.getUrl(), info)) {
                try (CallableStatement callableStatement = connection.prepareCall("CREATE EXTENSION \"uuid-ossp\";")) {
                    callableStatement.execute();
                } catch (SQLException e) {
                    // Ignore if already exists
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try (Connection connection = DriverManager.getConnection(configuration.getUrl(), info)) {
            try (CallableStatement st = connection.prepareCall("CREATE TYPE happiness AS ENUM ('happy', 'very_happy', 'ecstatic');")) {
                st.execute();
            } catch (SQLException e) {
                // Ignore if already exists
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return configuration;
    }
}
