package io.micronaut.data.r2dbc.postgres.vector;

import io.micronaut.context.annotation.Context;
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

        try {
            try (Connection connection = DriverManager.getConnection(url, info)) {
                try (CallableStatement callableStatement = connection.prepareCall("CREATE EXTENSION \"uuid-ossp\";")) {
                    callableStatement.execute();
                } catch (SQLException e) {
                    // Ignore if already exists
                }
                try (CallableStatement st = connection.prepareCall("CREATE TYPE happiness AS ENUM ('happy', 'very_happy', 'ecstatic');")) {
                    st.execute();
                } catch (SQLException e) {
                    // Ignore if already exists
                }
                try (CallableStatement st = connection.prepareCall("""
CREATE OR REPLACE PROCEDURE add1(IN myInput integer, OUT myOutput integer)
LANGUAGE plpgsql
AS $$
BEGIN
myOutput := myInput + 1;
END;
$$;

                 """)) {
                    st.execute();
                } catch (SQLException e) {
                    e.printStackTrace();
                    // Ignore if already exists
                }
                // Ensure pgvector extension and demo table for vector tests
                try (CallableStatement st = connection.prepareCall("CREATE EXTENSION IF NOT EXISTS vector;")) {
                    st.execute();
                } catch (SQLException e) {
                    // Ignore if not available or already exists
                }

            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return configuration;
    }
}
