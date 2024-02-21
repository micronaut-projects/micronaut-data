package io.micronaut.data.jdbc.oraclexe;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.jdbc.BasicJdbcConfiguration;
import jakarta.inject.Singleton;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

@Requires(notEnv = "oracle-jsonview")
@Singleton
public class OraceXEDbInit implements BeanCreatedEventListener<BasicJdbcConfiguration> {

    @Override
    public BasicJdbcConfiguration onCreated(BeanCreatedEvent<BasicJdbcConfiguration> event) {
        BasicJdbcConfiguration configuration = event.getBean();
        if (!configuration.getConfiguredDriverClassName().toLowerCase(Locale.ROOT).contains("oracle")) {
            return configuration;
        }

        final Properties info = new Properties();
        info.put("user", configuration.getUsername());
        info.put("password", configuration.getPassword());

        try {
            try (Connection connection = DriverManager.getConnection(configuration.getUrl(), info)) {
                try (CallableStatement st = connection.prepareCall("""
CREATE OR REPLACE PROCEDURE add1(myInput IN number, myOutput OUT number) IS
BEGIN
myOutput := myInput + 1;
END;
                 """)) {
                    st.execute();
                } catch (SQLException e) {
                    e.printStackTrace();
                    // Ignore if already exists
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return configuration;
    }
}
