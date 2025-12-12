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
        info.put("user", configuration.getValue(Option.valueOf("user")));
        info.put("password", configuration.getValue(Option.valueOf("password")));
        String host = (String) configuration.getValue(Option.valueOf("host"));
        Integer port = (Integer) configuration.getValue(Option.valueOf("port"));
        String database = (String) configuration.getValue(Option.valueOf("database"));

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;

        int attempts = 30;
        SQLException last = null;
        while (attempts-- > 0) {
            try (Connection connection = DriverManager.getConnection(url, info)) {
                // Ensure pgvector extension and demo table for vector tests
                try (CallableStatement st = connection.prepareCall("CREATE EXTENSION IF NOT EXISTS vector;")) {
                    st.execute();
                } catch (SQLException e) {
                    // Ignore if not available or already exists
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
}
