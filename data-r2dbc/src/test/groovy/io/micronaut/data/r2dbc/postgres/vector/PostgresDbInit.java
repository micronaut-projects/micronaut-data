package io.micronaut.data.r2dbc.postgres.vector;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import jakarta.inject.Singleton;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

@Context
@Singleton
@Requires(property = "spec.name", value = "PostgresR2dbcVectorEntitySpec")
public class PostgresDbInit implements BeanCreatedEventListener<ConnectionFactoryOptions> {

    @Override
    public ConnectionFactoryOptions onCreated(BeanCreatedEvent<ConnectionFactoryOptions> event) {
        ConnectionFactoryOptions configuration = event.getBean();

        final Properties info = new Properties();
        String user = requireOption(configuration, "user", String.class);
        String password = requireOption(configuration, "password", String.class);
        String host = requireOption(configuration, "host", String.class);
        Integer port = requireOption(configuration, "port", Integer.class);
        String database = requireOption(configuration, "database", String.class);
        info.put("user", user);
        info.put("password", password);

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;

        int attempts = 30;
        SQLException last = null;
        while (attempts-- > 0) {
            try (Connection connection = DriverManager.getConnection(url, info)) {
                // Ensure pgvector extension and demo table for vector tests
                try (CallableStatement st = connection.prepareCall("CREATE EXTENSION IF NOT EXISTS vector;")) {
                    st.execute();
                }
                last = null;
                break;
            } catch (SQLException e) {
                last = e;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
        if (last != null) {
            throw new RuntimeException(last);
        }
        return configuration;
    }

    private static <T> T requireOption(ConnectionFactoryOptions configuration, String optionName, Class<T> type) {
        Object value = configuration.getValue(Option.valueOf(optionName));
        if (value == null) {
            throw new IllegalStateException("Missing required R2DBC option: " + optionName);
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Invalid R2DBC option type for " + optionName + ": " + value.getClass().getName());
        }
        return type.cast(value);
    }
}
